package dev.crossfeed.core.net

import android.content.Context
import android.util.Log
import dev.crossfeed.core.AppleCatalog
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.history.HistoryDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

object Publisher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pushed = HashMap<String, Long>()

    fun offer(
        context: Context,
        title: String,
        artist: String?,
        album: String?,
        source: String,
        startedAt: Long,
        listenedMs: Long,
        durationMs: Long,
        rowId: Long? = null,
    ) {
        if (!Prefs(context).sharePlays || !Account(context).exists || Suspension.active) return
        val key = "$title|${artist.orEmpty()}"
        val last = pushed[key]
        if (listenedMs < FIRST_PUSH_MS) return
        if (last != null && listenedMs - last < REPUSH_MS) return
        pushed[key] = listenedMs
        scope.launch {
            val art = runCatching { remoteArt(context, title, artist) }.getOrNull()
            runCatching {
                Api.post(
                    context,
                    "/v1/posts",
                    JSONObject()
                        .put("title", title)
                        .put("artist", artist)
                        .put("album", album)
                        .put("art", art)
                        .put("source", source)
                        .put("startedAt", startedAt)
                        .put("listenedMs", listenedMs)
                        .put("durationMs", durationMs),
                )
            }.onSuccess { response ->
                val postId = response.optString("id")
                if (rowId != null && postId.isNotBlank()) {
                    runCatching { HistoryDb.get(context).linkPost(rowId, postId) }
                }
            }.onFailure {
                pushed.remove(key)
                Log.w("Publisher", "could not post $title", it)
            }
        }
    }

    fun remoteArt(context: Context, title: String, artist: String?): String? {
        val db = HistoryDb.get(context)
        val key = "$title|${artist.orEmpty()}".lowercase()
        db.remoteArt(key)?.let { return it.takeIf { url -> url.isNotBlank() } }
        val query = listOfNotNull(artist, title).joinToString(" ")
        val url = AppleCatalog.search(query, Prefs(context).country, limit = 1).firstOrNull()?.artwork
        db.setRemoteArt(key, url.orEmpty())
        return url
    }

    private const val FIRST_PUSH_MS = 20_000L
    private const val REPUSH_MS = 90_000L
}
