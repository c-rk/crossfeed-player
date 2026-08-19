package dev.crossfeed.core.history

import android.content.Context
import dev.crossfeed.core.AppleCatalog
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
        val match = guess?.let { (title, artist) ->
            val hit = runCatching {
                AppleCatalog.find(TrackMeta(title = title, artist = artist), Prefs(context).country)
            }.getOrNull()
            hit?.takeIf { it.score >= CONFIDENCE }?.let {
                Match(title = it.title, artist = it.artist, album = it.album, artwork = it.artwork)
            }
        }
        decided[key] = match
        return match
    }

    fun forget() = decided.clear()

    private const val CONFIDENCE = 0.7
}
