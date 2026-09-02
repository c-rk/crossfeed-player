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
    suspend fun refresh(context: Context, full: Boolean = false) = coroutineScope {
        val since = if (full) 0L else cursor
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

        val gotFeed = sync
        val gotLive = sync
        val gotTray = sync
        val gotPeople = people ?: sync


        // whatever went wrong, say the thing that went wrong. a generic line here was hiding
        // the difference between no friends, no signal, and not being signed in at all
        trouble = listOf(gotFeed, gotLive, gotTray, gotPeople)
            .firstNotNullOfOrNull { it.exceptionOrNull() }
            ?.let { why(it) }
        if (gotFeed.isSuccess || gotTray.isSuccess) loadedAt = System.currentTimeMillis()
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

    fun forget() {
        posts = emptyList()
        live = emptyList()
        alerts = Alerts(emptyList(), 0, 0)
        circle = Circle(emptyList(), emptyList(), emptyList())
        loadedAt = 0
    }
}
