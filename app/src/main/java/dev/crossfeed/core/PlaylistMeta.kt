package dev.crossfeed.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * The songs inside a shared playlist or album.
 *
 * Both services publish the running order on the public page — spotify in the embed payload,
 * apple in the page's structured data — so a shared playlist can be opened up without an api key
 * or an account anywhere.
 */
object PlaylistMeta {

    // dot has to cross newlines here, the way its twin in SourceMeta already does, or a payload
    // that happens to be printed over several lines is simply never found
    private val nextData = Regex(
        """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    fun tracks(url: String, limit: Int = 100): List<TrackMeta> {
        val list = when (LinkParser.platformOf(url)) {
            Platform.SPOTIFY -> spotify(url)
            Platform.APPLE_MUSIC -> apple(url)
            else -> emptyList()
        }
        return list.filter { it.title.isNotBlank() }.take(limit)
    }

    private fun spotify(url: String): List<TrackMeta> {
        val path = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("")
        val parts = path.trim('/').split('/')
        val type = parts.getOrNull(parts.size - 2) ?: return emptyList()
        val id = parts.lastOrNull()?.substringBefore('?') ?: return emptyList()
        val page = Http.get("https://open.spotify.com/embed/$type/$id") ?: return emptyList()
        val blob = nextData.find(page.body)?.groupValues?.get(1) ?: return emptyList()
        val root = runCatching { JSONObject(blob) }.getOrNull() ?: return emptyList()
        val items = findArray(root, "trackList") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            TrackMeta(
                title = item.optString("title"),
                artist = item.optString("subtitle").takeIf { it.isNotBlank() },
                durationMs = item.optInt("duration").takeIf { it > 0 },
            )
        }
    }

    private fun apple(url: String): List<TrackMeta> {
        val page = Http.get(url) ?: return emptyList()
        val node = Html.jsonLdObjects(page.body).firstOrNull { it.optJSONArray("track") != null }
            ?: return emptyList()
        val fallback = Json.firstName(node.opt("byArtist"))
        val items = node.optJSONArray("track") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val audio = item.optJSONObject("audio")
            TrackMeta(
                title = item.optString("name"),
                artist = Json.firstName(item.opt("byArtist"))
                    ?: audio?.let { Json.firstName(it.opt("byArtist")) }
                    ?: fallback,
                durationMs = Html.isoDurationMs(item.optString("duration")),
            )
        }
    }

    /** The payload nests differently between releases, so the list is looked for rather than walked to. */
    private fun findArray(root: JSONObject, key: String): JSONArray? {
        root.optJSONArray(key)?.let { return it }
        for (name in root.keys()) {
            when (val value = root.opt(name)) {
                is JSONObject -> findArray(value, key)?.let { return it }
                is JSONArray -> for (index in 0 until value.length()) {
                    val child = value.optJSONObject(index) ?: continue
                    findArray(child, key)?.let { return it }
                }
            }
        }
        return null
    }
}
