package dev.crossfeed.core.player

import android.content.Context

interface TrackSource {

    val id: String

    val label: String

    fun available(context: Context): Boolean = true

    suspend fun search(context: Context, query: String, limit: Int): List<Track>

    suspend fun resolve(context: Context, track: Track): Playable?
}

object Sources {

    private val registered = LinkedHashMap<String, TrackSource>()

    fun register(source: TrackSource) {
        registered[source.id] = source
    }

    fun unregister(id: String) {
        registered.remove(id)
    }

    fun all(): List<TrackSource> = registered.values.toList()

    fun byId(id: String): TrackSource? = registered[id]

    fun usable(context: Context): List<TrackSource> = all().filter { it.available(context) }

    suspend fun search(context: Context, query: String, limit: Int = 40): List<Track> =
        usable(context).flatMap { source ->
            runCatching { source.search(context, query, limit) }.getOrDefault(emptyList())
        }

    suspend fun resolve(context: Context, track: Track): Playable? =
        byId(track.sourceId)?.takeIf { it.available(context) }?.let { source ->
            runCatching { source.resolve(context, track) }.getOrNull()
        }
}
