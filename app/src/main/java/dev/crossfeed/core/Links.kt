package dev.crossfeed.core

import android.content.Context
import dev.crossfeed.core.catalog.Catalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class ServiceLink(
    val platform: Platform,
    val url: String,
    val exact: Boolean,
)

/**
 * The same song on every service crossfeed knows, worked out on our own stack. Apple and deezer
 * can be pinpointed from their catalogues; the rest get a search that lands on the song, and say
 * so rather than pretending.
 *
 * All five are asked at once. Two of them involve a lookup, and there is no reason the sheet
 * should wait for them one after another.
 */
object Links {

    suspend fun forTrack(context: Context, title: String, artist: String?): List<ServiceLink> =
        withContext(Dispatchers.IO) {
            val meta = TrackMeta(title = title, artist = artist)
            coroutineScope {
                Platform.entries.map { platform ->
                    async {
                        val address = Catalog.address(context, platform, meta)
                        ServiceLink(platform, address.url, address.exact)
                    }
                }.awaitAll()
            }
        }
}
