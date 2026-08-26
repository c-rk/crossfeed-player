package dev.crossfeed.core.catalog

import dev.crossfeed.core.Http
import dev.crossfeed.core.Json
import java.net.URLEncoder

/**
 * Deezer's public search, which needs no key and no account.
 *
 * It exists here because it is a genuinely different catalogue to Apple's rather than a mirror of
 * it, so a song missing from one is often present in the other, and because it answers quickly.
 */
object Deezer {

    fun search(query: String, limit: Int = 8): List<Catalog.Record> {
        if (query.isBlank()) return emptyList()
        val url = "https://api.deezer.com/search?q=" + URLEncoder.encode(query, "UTF-8") + "&limit=" + limit
        val body = Http.get(url, accept = "application/json")?.body ?: return emptyList()
        val list = Json.parse(body)?.optJSONArray("data") ?: return emptyList()

        return (0 until list.length()).mapNotNull { index ->
            val item = list.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val album = item.optJSONObject("album")
            Catalog.Record(
                title = title,
                artist = item.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() },
                album = album?.optString("title")?.takeIf { it.isNotBlank() },
                // deezer measures in seconds, everywhere else here is milliseconds
                durationMs = item.optInt("duration", 0).takeIf { it > 0 }?.times(1000),
                artwork = album?.let { cover(it) },
                source = "deezer",
            )
        }
    }

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

    private fun cover(album: org.json.JSONObject): String? =
        listOf("cover_big", "cover_medium", "cover_xl", "cover")
            .firstNotNullOfOrNull { album.optString(it).takeIf { url -> url.startsWith("https://") } }
}
