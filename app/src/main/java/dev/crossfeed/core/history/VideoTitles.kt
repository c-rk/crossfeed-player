package dev.crossfeed.core.history

import android.content.Context
import dev.crossfeed.core.catalog.Catalog

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

    // written from the capture scope, which runs a coroutine per session
    private val decided = java.util.Collections.synchronizedMap(HashMap<String, Match?>())

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

    /**
     * Youtube makes a topic channel for a real artist automatically and nobody else gets one, and
     * a vevo channel belongs to a label. Either is proof that whatever is playing is music,
     * whatever its title happens to say.
     */
    private fun musicChannel(name: String): Boolean {
        val tidy = name.trim().lowercase()
        return tidy.endsWith("topic") || tidy.endsWith("vevo")
    }

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
        val music = rawArtist != null && musicChannel(rawArtist)
        val match = guess?.let { (title, artist) ->
            val hit = runCatching {
                Catalog.look(context, title, artist, minScore = LOOSE)
            }.getOrNull()
            val exact = hit?.takeIf { it.score >= CONFIDENCE }?.let {
                Match(title = it.title, artist = it.artist, album = it.album, artwork = it.artwork)
            }
            // a short or local title scores badly however real it is, so the artist is asked about
            // instead: someone a shop lists as a recording artist is making music, whereas a
            // channel name or a game's timestamp is not.
            //
            // that question on its own is too generous, since plenty of plumbers and podcasts
            // share a name with a band. so it is only asked where the channel itself is proof of
            // music, or where some catalogue at least half recognised the title.
            exact ?: artist
                ?.takeIf { music || hit != null }
                ?.takeIf { Catalog.knownArtist(context, it) }
                ?.let { Match(title, it, null, null) }
        }
        decided[key] = match
        return match
    }


    fun forget() {
        decided.clear()
        Catalog.forget()
    }

    private const val CONFIDENCE = 0.62
    private const val LOOSE = 0.45
}
