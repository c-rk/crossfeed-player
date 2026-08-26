package dev.crossfeed.core.catalog

import android.content.Context
import dev.crossfeed.core.AppleCatalog
import dev.crossfeed.core.Matching
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.TrackMeta
import java.util.Locale

/**
 * One place to ask what a song is and what its sleeve looks like.
 *
 * Everything used to go to apple's search, in one storefront, and stop there. That is a single
 * point of failure for two features that matter: a play cannot reach the feed with artwork if the
 * one catalogue asked has never heard of it, and a video in a browser cannot be told apart from a
 * lecture without something to check it against. A release missing from one shop is often sitting
 * in another, so the shops are asked in turn until one of them knows.
 *
 * Order is deliberate. The listener's own storefront first, since that is where their subscription
 * is and where a link would take them. Then their region, then the largest shop, then a catalogue
 * built by different people entirely, then the open one that keeps what the shops drop.
 */
object Catalog {

    data class Record(
        val title: String,
        val artist: String?,
        val album: String? = null,
        val durationMs: Int? = null,
        val artwork: String? = null,
        val genre: String? = null,
        val source: String,
        val score: Double = 1.0,
    )

    private val found = HashMap<String, Record?>()
    private val artists = HashMap<String, Boolean>()

    /** Anything under this is not the song, whoever answered. */
    private const val FLOOR = 0.55

    /**
     * The best record any catalogue has of this song, or nothing if none of them recognise it.
     * A caller that will act on the answer, rather than only decorate with it, asks for a higher
     * score.
     */
    fun look(
        context: Context,
        title: String,
        artist: String?,
        durationMs: Int? = null,
        minScore: Double = FLOOR,
    ): Record? {
        if (title.isBlank()) return null
        val shops = storefronts(context)
        val key = listOf(title, artist.orEmpty(), shops.first(), minScore.toString())
            .joinToString("|").lowercase()
        if (found.containsKey(key)) return found[key]
        if (found.size > LIMIT) found.clear()

        val meta = TrackMeta(title = title, artist = artist, durationMs = durationMs)
        val query = meta.query
        val record = sequence {
            for (shop in shops) yield { apple(meta, shop) }
            yield { best(Deezer.search(query), meta) }
            yield { CoverArt.find(title, artist, durationMs)?.let { rate(it, meta) } }
        }.mapNotNull { runCatching { it() }.getOrNull() }
            .firstOrNull { it.score >= minScore }

        found[key] = record
        return record
    }

    /**
     * A sleeve for a song, from whichever catalogue has one.
     *
     * A confident match is preferred, but a plausible one beats an empty square in the feed, so a
     * loose search is the last thing tried before giving up.
     */
    fun art(context: Context, title: String, artist: String?, durationMs: Int? = null): String? {
        look(context, title, artist, durationMs)?.artwork?.let { return it }
        val query = TrackMeta(title = title, artist = artist).query
        for (shop in storefronts(context)) {
            val loose = runCatching { AppleCatalog.search(query, shop, limit = 1) }.getOrNull()
            loose?.firstOrNull()?.artwork?.let { return it }
        }
        return runCatching { Deezer.search(query, limit = 1) }.getOrNull()
            ?.firstOrNull()?.artwork
    }

    /**
     * Whether any shop lists a recording artist under this exact name.
     *
     * Only the shops are asked. The open catalogue would answer yes to almost any name, including
     * every person who has ever been credited on anything, which is the wrong question when the
     * point is to keep talks and match highlights out of the diary.
     */
    fun knownArtist(context: Context, name: String): Boolean {
        if (name.isBlank()) return false
        val key = name.lowercase()
        artists[key]?.let { return it }
        if (artists.size > LIMIT) artists.clear()

        val answer = storefronts(context).any { shop ->
            runCatching { AppleCatalog.knows(name, shop) }.getOrDefault(false)
        } || runCatching { Deezer.knows(name) }.getOrDefault(false)

        artists[key] = answer
        return answer
    }

    fun forget() {
        found.clear()
        artists.clear()
    }

    private fun apple(meta: TrackMeta, shop: String): Record? =
        AppleCatalog.find(meta, shop)?.let {
            Record(
                title = it.title,
                artist = it.artist,
                album = it.album,
                durationMs = it.durationMs,
                artwork = it.artwork,
                genre = it.genre,
                source = "apple:$shop",
                score = it.score,
            )
        }

    private fun best(records: List<Record>, meta: TrackMeta): Record? =
        records.map { rate(it, meta) }.maxByOrNull { it.score }

    private fun rate(record: Record, meta: TrackMeta): Record = record.copy(
        score = Matching.score(
            meta.title,
            meta.artist,
            meta.durationMs,
            record.title,
            record.artist,
            record.durationMs,
        ),
    )

    /** The listener's shop, then wherever their phone is, then the biggest one. */
    private fun storefronts(context: Context): List<String> {
        val chosen = Prefs(context).country
        val phone = Locale.getDefault().country.lowercase()
        return listOf(chosen, phone, "us").filter { it.length == 2 }.distinct()
    }

    private const val LIMIT = 500
}
