package dev.crossfeed.core.net

import android.content.Context
import android.util.Log
import dev.crossfeed.core.AppleCatalog
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.history.HistoryDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
        val prefs = Prefs(context)
        if (!prefs.sharePlays || prefs.sharingPaused) return
        if (!Account(context).exists || Suspension.active) return
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
                if (art == null) mendArt(context, title, artist, album, source, startedAt, listenedMs, durationMs)
            }.onFailure {
                pushed.remove(key)
                Log.w("Publisher", "could not post $title", it)
            }
        }
    }

    /**
     * Artwork for a track, from the catalogue, remembered so it is asked for once.
     *
     * A miss used to be remembered for ever, which meant a track the catalogue did not know on the
     * day it was first played could never gain a sleeve afterwards. Misses now expire, so a later
     * play asks again.
     */

    /**
     * A post that went out without a sleeve gets one more chance a few seconds later, once the
     * catalogue has had time to answer. Posting the same play again updates the row rather than
     * adding one, so the feed simply gains the artwork.
     */
    private fun mendArt(
        context: Context,
        title: String,
        artist: String?,
        album: String?,
        source: String,
        startedAt: Long,
        listenedMs: Long,
        durationMs: Long,
    ) {
        scope.launch {
            delay(6_000)
            val art = runCatching { remoteArt(context, title, artist) }.getOrNull() ?: return@launch
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
            }
        }
    }

    fun remoteArt(context: Context, title: String, artist: String?): String? {
        val db = HistoryDb.get(context)
        val key = "$title|${artist.orEmpty()}".lowercase()
        val cached = db.remoteArt(key)
        if (cached != null) {
            if (cached.isNotBlank() && !cached.startsWith(MISS)) return cached
            val missedAt = cached.removePrefix(MISS).toLongOrNull() ?: 0L
            if (System.currentTimeMillis() - missedAt < MISS_HOLDS_MS) return null
        }
        val query = listOfNotNull(artist, title).joinToString(" ")
        val url = AppleCatalog.search(query, Prefs(context).country, limit = 1).firstOrNull()?.artwork
        db.setRemoteArt(key, url ?: (MISS + System.currentTimeMillis()))
        return url
    }

    /**
     * Looks a sleeve up as soon as a song starts, so it is already in hand when the play is posted
     * twenty seconds later. Without this the first post of a song almost always went out bare and
     * only gained artwork on a later push, which never came if the song ended first.
     */
    fun warmArt(context: Context, title: String, artist: String?) {
        if (!Prefs(context).sharePlays) return
        scope.launch { runCatching { remoteArt(context, title, artist) } }
    }

    private const val FIRST_PUSH_MS = 20_000L
    private const val REPUSH_MS = 90_000L
    private const val MISS = "miss:"
    private const val MISS_HOLDS_MS = 7L * 24 * 3600_000
}
