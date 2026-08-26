package dev.crossfeed.core.catalog

import android.content.Context
import dev.crossfeed.core.AppleCatalog
import dev.crossfeed.core.Matching
import dev.crossfeed.core.Platform
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.TrackMeta
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Locale

/**
 * One place to ask what a song is, what its sleeve looks like, and where it can be heard.
 *
 * Everything used to go to apple's search, in one storefront, and stop there. That is a single
 * point of failure for four features at once: a play cannot reach the feed with artwork if the one
 * catalogue asked has never heard of it, a video in a browser cannot be told apart from a lecture
 * without something to check it against, a search only finds what one shop stocks, and a link can
 * only ever be precise for the one service that answers questions.
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
        /** Where this record lives, when the catalogue that answered has a page for it. */
        val url: String? = null,
        /** Which service that page belongs to, so a row can carry the right icon. */
        val platform: Platform? = null,
        /** Which catalogue answered. */
        val source: String,
        val score: Double = 1.0,
    )

    /** Where to send someone for a song, and whether it is the song or only a search for it. */
    data class Address(val url: String, val exact: Boolean)

    private val found = HashMap<String, Record?>()
    private val artists = HashMap<String, Boolean>()
    private val links = HashMap<String, String?>()

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
        return look(context, TrackMeta(title = title, artist = artist, durationMs = durationMs), minScore)
    }

    fun look(context: Context, meta: TrackMeta, minScore: Double = FLOOR): Record? {
        if (meta.title.isBlank()) return null
        val shops = storefronts(context)
        val key = listOf(meta.title, meta.artist.orEmpty(), meta.kind.name, shops.first(), minScore.toString())
            .joinToString("|").lowercase()
        if (found.containsKey(key)) return found[key]
        if (found.size > LIMIT) found.clear()

        val record = sequence {
            for (shop in shops) yield { apple(meta, shop) }
            yield { Deezer.find(meta) }
            yield { CoverArt.find(meta.title, meta.artist, meta.durationMs)?.let { rate(it, meta) } }
        }.mapNotNull { runCatching { it() }.getOrNull() }
            .firstOrNull { it.score >= minScore }

        found[key] = record
        return record
    }

    /**
     * Everything the shops have for a query, apple and deezer together, with the same record from
     * both folded into one row. Deezer is asked at the same time rather than afterwards, so the
     * second opinion costs nothing the listener can feel.
     */
    suspend fun search(context: Context, query: String, limit: Int = 25): List<Record> {
        if (query.isBlank()) return emptyList()
        val shop = storefronts(context).first()
        return coroutineScope {
            val apple = async {
                runCatching { AppleCatalog.search(query, shop, limit) }.getOrDefault(emptyList())
                    .map { it.toRecord(shop) }
            }
            val deezer = async {
                runCatching { Deezer.search(query, limit) }.getOrDefault(emptyList())
            }
            fold(apple.await(), deezer.await())
        }
    }

    /**
     * A precise address for a song on a service, where the service can be asked without a key.
     *
     * Apple and deezer both answer. Spotify, tidal and youtube music each want an account and a
     * registered application before they will say anything, which is a price this app does not
     * pay, so they get a search that lands on the song and say so plainly.
     */
    fun exact(context: Context, platform: Platform, meta: TrackMeta): String? {
        val key = listOf(platform.id, meta.title, meta.artist.orEmpty(), meta.kind.name)
            .joinToString("|").lowercase()
        if (links.containsKey(key)) return links[key]
        if (links.size > LIMIT) links.clear()

        val url = when (platform) {
            Platform.APPLE_MUSIC -> storefronts(context).firstNotNullOfOrNull { shop ->
                runCatching { AppleCatalog.find(meta, shop)?.url }.getOrNull()
            }

            Platform.DEEZER -> runCatching { Deezer.find(meta)?.url }.getOrNull()

            else -> null
        }
        links[key] = url
        return url
    }

    /** The song itself where that can be had, and a search that lands on it where it cannot. */
    fun address(context: Context, platform: Platform, meta: TrackMeta): Address {
        exact(context, platform, meta)?.let { return Address(it, true) }
        return Address(platform.searchUrl(meta.query, Prefs(context).country), false)
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
        links.clear()
    }

    /** The same song from two shops is one song, and the one with an apple link goes first. */
    private fun fold(apple: List<Record>, deezer: List<Record>): List<Record> {
        val out = apple.toMutableList()
        val seen = apple.map { mark(it) }.toMutableSet()
        for (record in deezer) {
            if (seen.add(mark(record))) out.add(record)
        }
        return out
    }

    private fun mark(record: Record): String =
        Matching.norm(record.title) + "|" + Matching.norm(record.artist)

    private fun apple(meta: TrackMeta, shop: String): Record? =
        AppleCatalog.find(meta, shop)?.toRecord(shop)

    private fun AppleCatalog.Hit.toRecord(shop: String) = Record(
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        artwork = artwork,
        genre = genre,
        url = url,
        platform = Platform.APPLE_MUSIC,
        source = "apple:$shop",
        score = score,
    )

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
