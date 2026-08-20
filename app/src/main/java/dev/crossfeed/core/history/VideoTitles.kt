package dev.crossfeed.core.history

import android.content.Context
import dev.crossfeed.core.AppleCatalog
import dev.crossfeed.core.Http
import dev.crossfeed.core.Json
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.TrackMeta

/**
 * Turning a video into a song, or refusing to.
 *
 * A browser session is every video there is, and its title is whatever the uploader typed. So a
 * candidate is cleaned into an artist and a title, then checked against the catalogue: if no real
 * record matches, it was not music and nothing is written down. That check is the whole feature —
 * without it a lecture and a football highlight land in the diary next to the songs.
 */
object VideoTitles {

    data class Match(
        val title: String,
        val artist: String?,
        val album: String?,
        val artwork: String?,
    )

    private val decided = HashMap<String, Match?>()

    private val brackets = Regex("\\[[^\\]]*\\]|\\((?:[^()]*)\\)")

    private val noise = Regex(
        "official\\s*(music)?\\s*(video|audio|visualiser|visualizer)|lyric[s]?\\s*video|" +
            "full\\s*(video\\s*)?song|video\\s*song|audio\\s*song|music\\s*video|" +
            "\\b4k\\b|\\bhd\\b|\\bhq\\b|\\bmv\\b|remaster(ed)?|extended|reupload",
        RegexOption.IGNORE_CASE,
    )

    private val channelNoise = Regex(
        "\\s*-\\s*topic$|vevo$|\\s*official$|\\s*music$|\\s*records$",
        RegexOption.IGNORE_CASE,
    )

    /** Splits a video title the way uploaders write them, before anything is looked up. */
    fun guess(rawTitle: String, rawArtist: String?): Pair<String, String?>? {
        var text = rawTitle.replace(brackets) { hit ->
            if (noise.containsMatchIn(hit.value)) "" else hit.value
        }
        text = text.substringBefore('|').trim()
        text = noise.replace(text, "").trim().trim('-', '·', '—', '–', ' ')
        if (text.isBlank()) return null

        val channel = rawArtist?.let { channelNoise.replace(it, "") }?.trim()?.takeIf { it.isNotEmpty() }

        // "artist - song" is the near universal shape, and a channel name is a weaker signal
        for (dash in listOf(" - ", " – ", " — ")) {
            val at = text.indexOf(dash)
            if (at <= 0) continue
            val left = text.take(at).trim()
            val right = text.drop(at + dash.length).trim()
            if (left.isNotEmpty() && right.isNotEmpty()) return right to left
        }
        return text to channel
    }

    /**
     * Confirms a guess against the catalogue. Both outcomes are remembered, so a video that is
     * not music costs one lookup rather than one per poll.
     */
    fun identify(context: Context, rawTitle: String, rawArtist: String?): Match? {
        val key = rawTitle.lowercase() + "|" + rawArtist.orEmpty().lowercase()
        if (decided.containsKey(key)) return decided[key]

        val guess = guess(rawTitle, rawArtist)
        val country = Prefs(context).country
        val match = guess?.let { (title, artist) ->
            val hit = runCatching {
                AppleCatalog.find(TrackMeta(title = title, artist = artist), country)
            }.getOrNull()
            val exact = hit?.takeIf { it.score >= CONFIDENCE }?.let {
                Match(title = it.title, artist = it.artist, album = it.album, artwork = it.artwork)
            }
            // a short or obscure title scores badly however real it is, so the artist is asked
            // about instead: someone the catalogue lists as a recording artist is making music,
            // whereas a channel name or a game's timestamp is not
            exact ?: artist?.takeIf { known(it, country) }?.let { Match(title, it, null, null) }
        }
        decided[key] = match
        return match
    }


    private val artists = HashMap<String, Boolean>()

    /** Whether the catalogue lists an artist under this exact name. */
    private fun known(name: String, country: String): Boolean {
        val key = name.lowercase()
        artists[key]?.let { return it }
        val term = java.net.URLEncoder.encode(name, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$term&entity=musicArtist&limit=5&country=$country"
        val body = Http.get(url, accept = "application/json")?.body
        val root = body?.let { Json.parse(it) }
        val results = root?.optJSONArray("results")
        var found = false
        if (results != null) {
            for (index in 0 until results.length()) {
                val listed = results.optJSONObject(index)?.optString("artistName").orEmpty()
                if (listed.equals(name, ignoreCase = true)) {
                    found = true
                    break
                }
            }
        }
        artists[key] = found
        return found
    }

    fun forget() {
        decided.clear()
        artists.clear()
    }

    private const val CONFIDENCE = 0.62
}
