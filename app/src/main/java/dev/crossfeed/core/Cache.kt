package dev.crossfeed.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class RecentEntry(
    val sourceUrl: String,
    val sourceLabel: String,
    val targetLabel: String,
    val title: String,
    val artist: String?,
    val exact: Boolean,
    val at: Long,
)

class Cache(context: Context) {

    private val store = context.applicationContext.getSharedPreferences("crossfeed_cache", Context.MODE_PRIVATE)

    fun get(url: String, targets: List<Platform>): Resolved? {
        val raw = store.getString(key(url, targets), null) ?: return null
        val json = Json.parse(raw) ?: return null
        val routes = json.optJSONArray("routes") ?: return null
        return Resolved(
            sourceUrl = url,
            source = Platform.byId(json.optString("source")),
            meta = json.optJSONObject("meta")?.let { readMeta(it) },
            routes = (0 until routes.length()).mapNotNull { index ->
                val item = routes.optJSONObject(index) ?: return@mapNotNull null
                val platform = Platform.byId(item.optString("platform")) ?: return@mapNotNull null
                Route(platform, item.optString("url"), item.optBoolean("exact"))
            },
        )
    }

    fun put(resolved: Resolved, targets: List<Platform>) {
        val routes = JSONArray()
        for (route in resolved.routes) {
            routes.put(
                JSONObject()
                    .put("platform", route.platform.id)
                    .put("url", route.url)
                    .put("exact", route.exact),
            )
        }
        val json = JSONObject()
            .put("source", resolved.source?.id)
            .put("routes", routes)
        resolved.meta?.let { json.put("meta", writeMeta(it)) }
        store.edit().putString(key(resolved.sourceUrl, targets), json.toString()).apply()
    }

    fun remember(entry: RecentEntry) {
        val list = recents().toMutableList()
        list.removeAll { it.sourceUrl == entry.sourceUrl }
        list.add(0, entry)
        val array = JSONArray()
        for (item in list.take(MAX_RECENTS)) {
            array.put(
                JSONObject()
                    .put("sourceUrl", item.sourceUrl)
                    .put("sourceLabel", item.sourceLabel)
                    .put("targetLabel", item.targetLabel)
                    .put("title", item.title)
                    .put("artist", item.artist)
                    .put("exact", item.exact)
                    .put("at", item.at),
            )
        }
        store.edit().putString(KEY_RECENTS, array.toString()).apply()
    }

    fun recents(): List<RecentEntry> {
        val raw = store.getString(KEY_RECENTS, null) ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            RecentEntry(
                sourceUrl = item.optString("sourceUrl"),
                sourceLabel = item.optString("sourceLabel"),
                targetLabel = item.optString("targetLabel"),
                title = item.optString("title"),
                artist = item.optString("artist").takeIf { it.isNotBlank() },
                exact = item.optBoolean("exact"),
                at = item.optLong("at"),
            )
        }
    }

    fun clear() = store.edit().clear().apply()

    private fun readMeta(json: JSONObject) = TrackMeta(
        title = json.optString("title"),
        artist = json.optString("artist").takeIf { it.isNotBlank() },
        album = json.optString("album").takeIf { it.isNotBlank() },
        durationMs = json.optInt("duration").takeIf { it > 0 },
        artwork = json.optString("artwork").takeIf { it.isNotBlank() },
        kind = runCatching { EntityKind.valueOf(json.optString("kind")) }.getOrDefault(EntityKind.UNKNOWN),
    )

    private fun writeMeta(meta: TrackMeta) = JSONObject()
        .put("title", meta.title)
        .put("artist", meta.artist)
        .put("album", meta.album)
        .put("duration", meta.durationMs ?: 0)
        .put("artwork", meta.artwork)
        .put("kind", meta.kind.name)

    private fun key(url: String, targets: List<Platform>) =
        targets.joinToString("+") { it.id } + "|" + url

    private companion object {
        const val KEY_RECENTS = "recents"
        const val MAX_RECENTS = 20
    }
}
