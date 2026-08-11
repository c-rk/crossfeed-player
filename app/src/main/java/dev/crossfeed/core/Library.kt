package dev.crossfeed.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class LibraryItem(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Int?,
    val local: LocalTrack? = null,
    val catalog: AppleCatalog.Hit? = null,
    val artwork: String? = null,
) {
    val sources: List<String>
        get() = listOfNotNull(local?.format, catalog?.let { "apple music" })
}

object Library {

    suspend fun search(context: Context, query: String): List<LibraryItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext recent(context)
        val country = Prefs(context).country
        coroutineScope {
            val localJob = async { LocalLibrary.search(context, query) }
            val catalogJob = async { AppleCatalog.search(query, country) }
            merge(localJob.await(), catalogJob.await())
        }
    }

    suspend fun recent(context: Context): List<LibraryItem> = withContext(Dispatchers.IO) {
        LocalLibrary.recent(context).map { it.toItem() }
    }

    private fun merge(local: List<LocalTrack>, catalog: List<AppleCatalog.Hit>): List<LibraryItem> {
        val items = local.map { it.toItem() }.toMutableList()
        for (hit in catalog) {
            val index = items.indexOfFirst { item ->
                item.catalog == null && Matching.score(
                    item.title,
                    item.artist,
                    item.durationMs,
                    hit.title,
                    hit.artist,
                    hit.durationMs,
                ) >= 0.8
            }
            if (index >= 0) {
                items[index] = items[index].copy(
                    catalog = hit,
                    artwork = items[index].artwork ?: hit.artwork,
                )
            } else {
                items.add(
                    LibraryItem(
                        title = hit.title,
                        artist = hit.artist,
                        album = hit.album,
                        durationMs = hit.durationMs,
                        catalog = hit,
                        artwork = hit.artwork,
                    ),
                )
            }
        }
        return items.sortedByDescending { (if (it.local != null) 2 else 0) + (if (it.catalog != null) 1 else 0) }
    }

    private fun LocalTrack.toItem() = LibraryItem(
        title = title,
        artist = artist,
        album = album,
        durationMs = durationMs,
        local = this,
    )
}
