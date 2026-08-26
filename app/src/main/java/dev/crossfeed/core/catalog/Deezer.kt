package dev.crossfeed.core.catalog

import dev.crossfeed.core.EntityKind
import dev.crossfeed.core.Http
import dev.crossfeed.core.Json
import dev.crossfeed.core.Matching
import dev.crossfeed.core.Platform
import dev.crossfeed.core.TrackMeta
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Deezer's public search, which needs no key and no account.
 *
 * It earns its place twice over. It is a genuinely different catalogue to apple's rather than a
 * mirror of it, so a song missing from one is often sitting in the other. And it hands back the
 * address of the record itself, which means a deezer link can point at the song instead of at a
 * search box, something only apple could manage before.
 */
object Deezer {

    fun search(query: String, limit: Int = 8, kind: EntityKind = EntityKind.TRACK): List<Catalog.Record> {
        if (query.isBlank()) return emptyList()
        val album = kind == EntityKind.ALBUM
        val path = if (album) "search/album" else "search"
        val url = "https://api.deezer.com/$path?q=" + URLEncoder.encode(query, "UTF-8") + "&limit=" + limit
        val body = Http.get(url, accept = "application/json")?.body ?: return emptyList()
        val list = Json.parse(body)?.optJSONArray("data") ?: return emptyList()

        return (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            if (album) toAlbum(item) else toTrack(item)
        }
    }

    /** The one record this is, if deezer is confident enough about which one it is. */
    fun find(meta: TrackMeta): Catalog.Record? =
        search(meta.query, limit = 12, kind = meta.kind)
            .map { record ->
                record.copy(
                    score = Matching.score(
                        meta.title,
                        meta.artist,
                        meta.durationMs,
                        record.title,
                        record.artist,
                        record.durationMs,
                    ),
                )
            }
            .maxByOrNull { it.score }
            ?.takeIf { it.score >= 0.55 }

    /** Whether deezer lists a recording artist under this exact name. */
    fun knows(name: String): Boolean {
        val url = "https://api.deezer.com/search/artist?q=" + URLEncoder.encode(name, "UTF-8") + "&limit=5"
        val body = Http.get(url, accept = "application/json")?.body ?: return false
        val list = Json.parse(body)?.optJSONArray("data") ?: return false
        for (index in 0 until list.length()) {
            val listed = list.optJSONObject(index)?.optString("name").orEmpty()
            if (listed.equals(name, ignoreCase = true)) return true
        }
        return false
    }

    private fun toTrack(item: JSONObject): Catalog.Record? {
        val title = item.optString("title").takeIf { it.isNotBlank() } ?: return null
        val album = item.optJSONObject("album")
        return Catalog.Record(
            title = title,
            artist = item.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() },
            album = album?.optString("title")?.takeIf { it.isNotBlank() },
            // deezer measures in seconds, everywhere else here is milliseconds
            durationMs = item.optInt("duration", 0).takeIf { it > 0 }?.times(1000),
            artwork = album?.let { cover(it) },
            url = item.optString("link").takeIf { it.startsWith("https://") },
            platform = Platform.DEEZER,
            source = "deezer",
        )
    }

    private fun toAlbum(item: JSONObject): Catalog.Record? {
        val title = item.optString("title").takeIf { it.isNotBlank() } ?: return null
        return Catalog.Record(
            title = title,
            artist = item.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() },
            album = title,
            artwork = cover(item),
            url = item.optString("link").takeIf { it.startsWith("https://") },
            platform = Platform.DEEZER,
            source = "deezer",
        )
    }

    private fun cover(holder: JSONObject): String? =
        listOf("cover_big", "cover_medium", "cover_xl", "cover")
            .firstNotNullOfOrNull { size -> holder.optString(size).takeIf { it.startsWith("https://") } }
}
