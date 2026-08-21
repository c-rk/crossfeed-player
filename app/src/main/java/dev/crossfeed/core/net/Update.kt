package dev.crossfeed.core.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.crossfeed.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Notice(
    val text: String,
    val link: String?,
    val at: Long,
)

/**
 * The one notice the app will show, written by hand on the server.
 *
 * There is no update check as such: the phone asks what the notice is, and the answer is usually
 * nothing. Dismissing one is remembered by when it was posted, so taking it down and putting a
 * different one up later still reaches someone who waved the first one away.
 */
object Update {

    var notice by mutableStateOf<Notice?>(null)
        private set

    suspend fun check(context: Context) = withContext(Dispatchers.IO) {
        val fetched = runCatching {
            val body = Api.get(context, "/v1/banner").optJSONObject("banner") ?: return@runCatching null
            val text = body.optString("text").takeIf { it.isNotBlank() } ?: return@runCatching null
            Notice(
                text = text,
                link = body.stringOrNull("link"),
                at = body.optLong("at"),
            )
        }.getOrNull()
        notice = fetched?.takeIf { it.at > Prefs(context).noticeSeen }
    }

    fun dismiss(context: Context) {
        notice?.let { Prefs(context).noticeSeen = it.at }
        notice = null
    }
}
