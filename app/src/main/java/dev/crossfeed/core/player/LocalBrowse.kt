package dev.crossfeed.core.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dev.crossfeed.core.LocalLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Category { SONGS, ALBUMS, ARTISTS, FOLDERS }

data class Bucket(
    val key: String,
    val title: String,
    val subtitle: String?,
    val artwork: String?,
    val count: Int,
)

object LocalBrowse {

    private const val MUSIC = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
        "${MediaStore.Audio.Media.DURATION} >= 45000"

    private val columns = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
    )

    suspend fun songs(context: Context, limit: Int = 2000): List<Track> = withContext(Dispatchers.IO) {
        query(context, null, null, "${MediaStore.Audio.Media.TITLE} ASC", limit)
    }

    suspend fun buckets(context: Context, category: Category): List<Bucket> = withContext(Dispatchers.IO) {
        if (!LocalLibrary.hasPermission(context)) return@withContext emptyList()
        val tracks = query(context, null, null, "${MediaStore.Audio.Media.TITLE} ASC", 4000)
        when (category) {
            Category.ALBUMS -> tracks.groupBy { it.album ?: "unknown album" }.map { (album, items) ->
                Bucket(album, album, items.firstOrNull()?.artist, items.firstOrNull()?.artwork, items.size)
            }.sortedBy { it.title.lowercase() }

            Category.ARTISTS -> tracks.groupBy { it.artist ?: "unknown artist" }.map { (artist, items) ->
                Bucket(artist, artist, "${items.size} tracks", items.firstOrNull()?.artwork, items.size)
            }.sortedBy { it.title.lowercase() }

            Category.FOLDERS -> tracks.groupBy { folderOf(it.ref) }.map { (folder, items) ->
                Bucket(folder, folder.substringAfterLast('/'), folder, items.firstOrNull()?.artwork, items.size)
            }.sortedBy { it.title.lowercase() }

            Category.SONGS -> emptyList()
        }
    }

    suspend fun tracksIn(context: Context, category: Category, key: String): List<Track> =
        withContext(Dispatchers.IO) {
            when (category) {
                Category.ALBUMS -> query(
                    context,
                    "${MediaStore.Audio.Media.ALBUM} = ?",
                    arrayOf(key),
                    "${MediaStore.Audio.Media.TRACK} ASC",
                    500,
                )

                Category.ARTISTS -> query(
                    context,
                    "${MediaStore.Audio.Media.ARTIST} = ?",
                    arrayOf(key),
                    "${MediaStore.Audio.Media.ALBUM} ASC",
                    500,
                )

                Category.FOLDERS -> query(context, null, null, "${MediaStore.Audio.Media.TITLE} ASC", 4000)
                    .filter { folderOf(it.ref) == key }

                Category.SONGS -> emptyList()
            }
        }

    private fun folderOf(ref: String): String = ref.substringBeforeLast('/', "").ifBlank { "phone" }

    private fun query(
        context: Context,
        where: String?,
        args: Array<String>?,
        order: String,
        limit: Int,
    ): List<Track> {
        if (!LocalLibrary.hasPermission(context)) return emptyList()
        val selection = if (where == null) MUSIC else "$MUSIC AND $where"
        val out = mutableListOf<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            columns,
            selection,
            args,
            order,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (cursor.moveToNext() && out.size < limit) {
                val id = cursor.getLong(idCol)
                val uri: Uri =
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                out.add(
                    Track(
                        id = "local:$id",
                        title = cursor.getString(titleCol) ?: "",
                        artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" },
                        album = cursor.getString(albumCol),
                        durationMs = cursor.getLong(durationCol),
                        artwork = uri.toString(),
                        sourceId = "local",
                        ref = cursor.getString(dataCol) ?: uri.toString(),
                    ).let { if (it.ref.startsWith("/")) it.copy(ref = uri.toString()) else it },
                )
            }
        }
        return out
    }
}
