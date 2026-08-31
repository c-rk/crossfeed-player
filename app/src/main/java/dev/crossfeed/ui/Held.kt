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
import dev.crossfeed.core.net.Dm
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

    var dms by mutableStateOf<List<Dm>>(emptyList())
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
    suspend fun refresh(context: Context) = coroutineScope {
        val feed = async { runCatching { Social.feed(context) } }
        val playing = async { runCatching { Social.live(context) } }
        val tray = async { runCatching { Social.alerts(context) } }
        val people = async { runCatching { Social.circle(context) } }
        val sent = async { runCatching { Social.dms(context) } }

        val gotFeed = feed.await()
        val gotLive = playing.await()
        val gotTray = tray.await()
        val gotPeople = people.await()
        val gotDms = sent.await()

        gotFeed.getOrNull()?.let { posts = it }
        gotLive.getOrNull()?.let { live = it }
        gotTray.getOrNull()?.let { alerts = it }
        gotPeople.getOrNull()?.let { circle = it }
        gotDms.getOrNull()?.let { dms = it }

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

    /** Applied straight away so a tap does not wait on a round trip to look like it worked. */
    fun replace(updated: List<Post>) {
        posts = updated
    }

    fun seen() {
        alerts = alerts.copy(unread = 0)
    }

    fun forget() {
        posts = emptyList()
        live = emptyList()
        alerts = Alerts(emptyList(), 0, 0)
        circle = Circle(emptyList(), emptyList(), emptyList())
        dms = emptyList()
        loadedAt = 0
    }
}
