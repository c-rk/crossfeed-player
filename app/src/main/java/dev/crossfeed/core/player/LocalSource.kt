package dev.crossfeed.core.player

import android.content.Context
import dev.crossfeed.core.LocalLibrary
import dev.crossfeed.core.LocalTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object LocalSource : TrackSource {

    override val id = "local"

    override val label = "my library"

    override fun available(context: Context) = LocalLibrary.hasPermission(context)

    override suspend fun search(context: Context, query: String, limit: Int): List<Track> =
        withContext(Dispatchers.IO) {
            val found = if (query.isBlank()) {
                LocalLibrary.recent(context, limit)
            } else {
                LocalLibrary.search(context, query, limit)
            }
            found.map { it.toTrack() }
        }

    override suspend fun resolve(context: Context, track: Track): Playable =
        Playable(uri = track.ref, durationMs = track.durationMs)

    private fun LocalTrack.toTrack() = Track(
        id = "local:$id",
        title = title,
        artist = artist,
        album = album,
        durationMs = (durationMs ?: 0).toLong(),
        artwork = uri.toString(),
        sourceId = "local",
        ref = uri.toString(),
    )
}
