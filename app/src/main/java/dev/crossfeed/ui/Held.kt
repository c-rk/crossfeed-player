package dev.crossfeed.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.crossfeed.core.history.Dashboard
import dev.crossfeed.core.history.Range
import dev.crossfeed.core.history.Stats
import dev.crossfeed.core.net.Alerts
import dev.crossfeed.core.net.ApiError
import dev.crossfeed.core.net.Circle
import dev.crossfeed.core.net.Person
import dev.crossfeed.core.net.Live
import dev.crossfeed.core.net.Post
import dev.crossfeed.core.net.Social
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * What the pages already know, kept where swiping cannot throw it away.
 *
 * The four pages live in a pager, which keeps only the ones next to you and drops the rest. That
 * is right for layout and wrong for state: a page rebuilt from nothing shows an empty diary and an
 * empty feed for as long as a database read and a round trip take, every single time you swipe
 * back to it. So what was last read is held here instead, the page paints it immediately, and the
 * refresh happens underneath.
 */
object Diary {

    private val byRange = mutableMapOf<Range, Dashboard>()

    var week by mutableStateOf<List<Long>>(emptyList())
        private set

    var showing by mutableStateOf<Dashboard?>(null)
        private set

    fun cached(range: Range): Dashboard? = byRange[range]

    suspend fun load(context: Context, range: Range, spark: suspend () -> List<Long>) {
        byRange[range]?.let { showing = it }
        val fresh = runCatching { Stats.load(context, range, "") }.getOrNull() ?: return
        byRange[range] = fresh
        showing = fresh
        week = runCatching { spark() }.getOrDefault(week)
    }

    /** After a play is written or the diary is tidied, what is held is no longer true. */
    fun forget() {
        byRange.clear()
        showing = null
    }
}

object Aux {

    var posts by mutableStateOf<List<Post>>(emptyList())
        private set

    var live by mutableStateOf<List<Live>>(emptyList())
        private set

    var alerts by mutableStateOf(Alerts(emptyList(), 0, 0))
        private set

    var circle by mutableStateOf(Circle(emptyList(), emptyList(), emptyList()))
        private set

    var trouble by mutableStateOf<String?>(null)
        private set

    var loadedAt by mutableStateOf(0L)
        private set

    /**
     * Everything the aux page shows, asked for at once rather than one after another.
     *
     * Four round trips in a row is four times the wait for no reason: none of them depends on
     * another. A failure leaves what was already there on screen rather than blanking the page,
     * because a stale feed is more use than an empty one, and says so quietly.
     */
    private var cursor = 0L
    private var peopleAt = 0L

    /**
     * Everything the aux page shows, in as few questions as it can be asked in.
     *
     * It used to ask four separate things every few seconds, one of which counts every reaction on
     * every post it returns. That is a lot of database for a page nobody is looking at, and it ran
     * whether you were looking or not. Now it asks the one endpoint built for this, which answers
     * with only what has changed since last time, and the circle is only re-read now and then
     * because friendships do not change by the second.
     */
    /** Answers whether anything actually changed, so a caller can slow down when nothing does. */
    suspend fun refresh(context: Context, full: Boolean = false): Boolean = coroutineScope {
        if (cursor == 0L) restore(context)
        // asking for everything is only right when there is nothing, since the sync answers with
        // what changed and what is already here is still true
        val since = if (full || posts.isEmpty()) 0L else cursor
        val sync = runCatching { Social.sync(context, since) }

        val stale = System.currentTimeMillis() - peopleAt > PEOPLE_EVERY_MS
        val people = if (full || stale) {
            async { runCatching { Social.circle(context) } }.await()
        } else {
            null
        }

        sync.getOrNull()?.let { fresh ->
            cursor = fresh.now
            if (since == 0L) {
                posts = fresh.posts
            } else if (fresh.posts.isNotEmpty()) {
                // what came back is what changed, so it goes on top of what was already here
                val changed = fresh.posts.associateBy { it.id }
                val kept = posts.filterNot { it.id in changed }
                posts = (fresh.posts + kept).sortedByDescending { it.updatedAt }
            }
            fresh.live?.let { live = it }
            alerts = alerts.copy(unread = fresh.unread, requests = fresh.requests)
        }

        people?.getOrNull()?.let { circle = it; peopleAt = System.currentTimeMillis() }

        if (sync.isSuccess) {
            loadedAt = System.currentTimeMillis()
            keep(context)
        }
        // whatever went wrong, say the thing that went wrong. a generic line here was hiding
        // the difference between no friends, no signal, and not being signed in at all
        trouble = listOfNotNull(sync, people)
            .firstNotNullOfOrNull { it.exceptionOrNull() }
            ?.let { why(it) }

        sync.getOrNull()?.posts?.isNotEmpty() == true
    }

    private fun why(error: Throwable): String {
        val said = error.message.orEmpty().ifBlank { "something went wrong" }
        return when ((error as? ApiError)?.code) {
            0 -> "cannot reach the server"
            401, 403 -> "this phone is not signed in: $said"
            404 -> "the server does not know that: $said"
            else -> said
        }
    }

    /**
     * Reactions the phone has made but the server has not confirmed yet.
     *
     * The feed refetches every few seconds, and a refresh that landed between a tap and the write
     * reaching the server used to wipe the tap off the screen: you pressed a thing, it lit up, and
     * a moment later it went out again for no reason you could see. What is still in flight is
     * held back from being overwritten until the write finishes.
     */
    private val inFlight = mutableSetOf<String>()

    /** Applied straight away so a tap does not wait on a round trip to look like it worked. */
    fun replace(updated: List<Post>, pending: String? = null) {
        pending?.let { inFlight.add(it) }
        posts = updated
    }

    fun settled(postId: String) {
        inFlight.remove(postId)
    }

    fun seen() {
        alerts = alerts.copy(unread = 0)
    }

    /** The tray's contents, asked for only when somebody opens it. */
    suspend fun openTray(context: Context) {
        runCatching { Social.alerts(context) }.getOrNull()?.let { alerts = it }
    }

    private const val PEOPLE_EVERY_MS = 60_000L

    /**
     * What was on screen last time, written down.
     *
     * Holding the feed in memory survives a swipe but not the app being closed, and a cold start
     * that shows an empty aux for two seconds reads as broken rather than as loading. This is a
     * few hundred lines of json, written after each sync and read once at the start, so the page
     * opens on what you last saw and then catches up.
     */
    private fun file(context: Context) = java.io.File(context.filesDir, "aux.json")

    private fun keep(context: Context) {
        runCatching {
            val out = org.json.JSONObject()
            out.put("at", System.currentTimeMillis())
            out.put("posts", org.json.JSONArray().apply { posts.take(40).forEach { put(it.row()) } })
            out.put("live", org.json.JSONArray().apply { live.forEach { put(it.row()) } })
            out.put("handles", org.json.JSONArray().apply { circle.accepted.forEach { put(it.handle) } })
            file(context).writeText(out.toString())
        }
    }

    private fun restore(context: Context) {
        if (posts.isNotEmpty()) return
        runCatching {
            val held = file(context).takeIf { it.exists() }?.readText() ?: return
            val root = org.json.JSONObject(held)
            // what was written down was true at the moment it was written, and saying so is the
            // difference between a stale page and a lying one
            loadedAt = root.optLong("at")
            val kept = root.optJSONArray("posts") ?: return
            posts = (0 until kept.length()).mapNotNull { kept.optJSONObject(it)?.toPost() }
            val playing = root.optJSONArray("live")
            if (playing != null) {
                live = (0 until playing.length()).mapNotNull { playing.optJSONObject(it)?.toLive() }
            }
            val names = root.optJSONArray("handles")
            if (names != null && circle.accepted.isEmpty()) {
                circle = circle.copy(
                    accepted = (0 until names.length()).map {
                        Person(id = "", handle = names.optString(it), display = names.optString(it))
                    },
                )
            }
        }
    }

    private fun Post.row() = org.json.JSONObject()
        .put("id", id).put("handle", handle).put("title", title).put("artist", artist)
        .put("album", album).put("art", art).put("source", source)
        .put("listenedMs", listenedMs).put("durationMs", durationMs).put("updatedAt", updatedAt)
        .put("reactions", reactions).put("mine", mine).put("self", self)
        .put("counts", org.json.JSONObject(counts.mapValues { it.value }))

    private fun org.json.JSONObject.toPost(): Post? {
        val id = optString("id").takeIf { it.isNotBlank() } ?: return null
        val tally = optJSONObject("counts")
        return Post(
            id = id,
            handle = optString("handle"),
            title = optString("title"),
            artist = optString("artist").takeIf { it.isNotBlank() },
            album = optString("album").takeIf { it.isNotBlank() },
            art = optString("art").takeIf { it.isNotBlank() },
            source = optString("source").takeIf { it.isNotBlank() },
            listenedMs = optLong("listenedMs"),
            durationMs = optLong("durationMs"),
            updatedAt = optLong("updatedAt"),
            reactions = optInt("reactions"),
            mine = optString("mine").takeIf { it.isNotBlank() },
            counts = tally?.keys()?.asSequence()?.associateWith { tally.optInt(it) }.orEmpty(),
            self = optBoolean("self"),
        )
    }

    private fun Live.row() = org.json.JSONObject()
        .put("handle", handle).put("title", title).put("artist", artist).put("art", art)
        .put("source", source).put("at", at).put("self", self)
        .put("listenedMs", listenedMs).put("durationMs", durationMs)

    private fun org.json.JSONObject.toLive(): Live? {
        val handle = optString("handle").takeIf { it.isNotBlank() } ?: return null
        return Live(
            handle = handle,
            title = optString("title"),
            artist = optString("artist").takeIf { it.isNotBlank() },
            art = optString("art").takeIf { it.isNotBlank() },
            source = optString("source").takeIf { it.isNotBlank() },
            at = optLong("at"),
            self = optBoolean("self"),
            listenedMs = optLong("listenedMs"),
            durationMs = optLong("durationMs"),
        )
    }

    fun forget() {
        posts = emptyList()
        live = emptyList()
        alerts = Alerts(emptyList(), 0, 0)
        circle = Circle(emptyList(), emptyList(), emptyList())
        loadedAt = 0
    }
}
