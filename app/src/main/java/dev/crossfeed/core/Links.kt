package dev.crossfeed.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ServiceLink(
    val platform: Platform,
    val url: String,
    val exact: Boolean,
)

/**
 * The same song on every service crossfeed knows, worked out on our own stack. Apple can be
 * pinpointed from the catalogue; the rest get a search that lands on the song, and say so.
 */
object Links {

    suspend fun forTrack(context: Context, title: String, artist: String?): List<ServiceLink> =
        withContext(Dispatchers.IO) {
            val country = Prefs(context).country
            val meta = TrackMeta(title = title, artist = artist)
            Platform.entries.map { platform ->
                if (platform == Platform.APPLE_MUSIC) {
                    AppleCatalog.find(meta, country)
                        ?.let { ServiceLink(platform, it.url, true) }
                        ?: ServiceLink(platform, platform.searchUrl(meta.query), false)
                } else {
                    ServiceLink(platform, platform.searchUrl(meta.query), false)
                }
            }
        }
}
