package dev.crossfeed.core

import org.json.JSONObject
import java.net.URLEncoder

object AppleCatalog {

    data class Hit(
        val url: String,
        val title: String,
        val artist: String,
        val album: String?,
        val artwork: String?,
        val durationMs: Int? = null,
        val genre: String? = null,
        val score: Double = 1.0,
    )

    private fun artworkOf(item: JSONObject): String? =
        item.optString("artworkUrl100").takeIf { it.isNotBlank() }?.replace("100x100", "300x300")

    fun search(query: String, country: String, limit: Int = 20): List<Hit> {
        if (query.isBlank()) return emptyList()
        val term = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$term&entity=song&limit=$limit&country=$country"
        val response = Http.get(url, accept = "application/json") ?: return emptyList()
        val root = Json.parse(response.body) ?: return emptyList()
        val results = root.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { index ->
            val item = results.optJSONObject(index) ?: return@mapNotNull null
            val title = item.optString("trackName").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val link = item.optString("trackViewUrl").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Hit(
                url = link.substringBefore("&uo="),
                title = title,
                artist = item.optString("artistName"),
                album = item.optString("collectionName").takeIf { it.isNotBlank() },
                artwork = artworkOf(item),
                durationMs = item.optInt("trackTimeMillis", 0).takeIf { it > 0 },
                genre = item.optString("primaryGenreName").takeIf { it.isNotBlank() },
                score = 1.0,
            )
        }
    }

    /**
     * Whether the shop lists a recording artist under this exact name. A short or local title
     * scores badly however real it is, so the person is asked about when the song cannot be found.
     */
    fun knows(name: String, country: String): Boolean {
        if (name.isBlank()) return false
        val term = URLEncoder.encode(name, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$term&entity=musicArtist&limit=5&country=$country"
        val response = Http.get(url, accept = "application/json") ?: return false
        val results = Json.parse(response.body)?.optJSONArray("results") ?: return false
        for (index in 0 until results.length()) {
            val listed = results.optJSONObject(index)?.optString("artistName").orEmpty()
            if (listed.equals(name, ignoreCase = true)) return true
        }
        return false
    }

    fun find(meta: TrackMeta, country: String): Hit? {
        val album = meta.kind == EntityKind.ALBUM
        val entity = if (album) "album" else "song"
        val term = URLEncoder.encode(meta.query, "UTF-8")
        val url = "https://itunes.apple.com/search?term=$term&entity=$entity&limit=12&country=$country"
        val response = Http.get(url, accept = "application/json") ?: return null
        val root = Json.parse(response.body) ?: return null
        val results = root.optJSONArray("results") ?: return null

        var best: Hit? = null
        for (i in 0 until results.length()) {
            val item = results.optJSONObject(i) ?: continue
            val hit = toHit(item, meta, album) ?: continue
            if (best == null || hit.score > best!!.score) best = hit
        }
        return best?.takeIf { it.score >= 0.55 }
    }

    private fun toHit(item: JSONObject, meta: TrackMeta, album: Boolean): Hit? {
        val title = if (album) item.optString("collectionName") else item.optString("trackName")
        if (title.isBlank()) return null
        val artist = item.optString("artistName")
        val link = if (album) item.optString("collectionViewUrl") else item.optString("trackViewUrl")
        if (link.isBlank()) return null
        val duration = item.optInt("trackTimeMillis", 0).takeIf { it > 0 }
        val score = Matching.score(meta.title, meta.artist, meta.durationMs, title, artist, duration)
        return Hit(
            url = link.substringBefore("&uo="),
            title = title,
            artist = artist,
            album = item.optString("collectionName").takeIf { it.isNotBlank() },
            artwork = artworkOf(item),
            durationMs = duration,
            genre = item.optString("primaryGenreName").takeIf { it.isNotBlank() },
            score = score,
        )
    }
}
