package dev.crossfeed.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PlayRoute(
    val local: LocalTrack?,
    val platform: Platform?,
    val url: String?,
    val exact: Boolean,
)

object Router {

    suspend fun routeFor(context: Context, title: String, artist: String?): PlayRoute =
        withContext(Dispatchers.IO) {
            val prefs = Prefs(context)
            val meta = TrackMeta(title = title, artist = artist)

            if (prefs.preferLocal) {
                LocalLibrary.find(context, meta)?.let {
                    return@withContext PlayRoute(it, null, null, true)
                }
            }

            val target = prefs.primary
            if (target == Platform.APPLE_MUSIC) {
                AppleCatalog.find(meta, prefs.country)?.let {
                    return@withContext PlayRoute(null, target, it.url, true)
                }
            }
            PlayRoute(null, target, target.searchUrl(meta.query), false)
        }

    suspend fun play(context: Context, title: String, artist: String?) {
        val route = routeFor(context, title, artist)
        withContext(Dispatchers.Main) {
            when {
                route.local != null -> Opener.openLocal(context, route.local)
                route.platform != null && route.url != null -> Opener.open(context, route.platform, route.url)
            }
        }
    }
}
