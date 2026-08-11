package dev.crossfeed.core

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

data class LocalTrack(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Int?,
    val mimeType: String?,
    val uri: Uri,
) {
    val format: String
        get() = when {
            mimeType == null -> "local"
            mimeType.contains("flac") -> "flac"
            mimeType.contains("mpeg") -> "mp3"
            mimeType.contains("mp4") || mimeType.contains("m4a") || mimeType.contains("aac") -> "m4a"
            mimeType.contains("ogg") || mimeType.contains("vorbis") -> "ogg"
            mimeType.contains("opus") -> "opus"
            mimeType.contains("aiff") -> "aiff"
            mimeType.contains("wav") -> "wav"
            else -> mimeType.substringAfterLast('/')
        }
}

object LocalLibrary {

    private val music = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
        "${MediaStore.Audio.Media.DURATION} >= 45000 AND " +
        "${MediaStore.Audio.Media.DATA} NOT LIKE '%WhatsApp%' AND " +
        "${MediaStore.Audio.Media.DATA} NOT LIKE '%Recordings%' AND " +
        "${MediaStore.Audio.Media.DATA} NOT LIKE '%Telegram%'"

    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.MIME_TYPE,
    )

    fun permission(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission()) == PackageManager.PERMISSION_GRANTED

    fun find(context: Context, meta: TrackMeta, threshold: Double = 0.72): LocalTrack? {
        if (!hasPermission(context)) return null
        val token = Matching.norm(meta.title).split(' ').maxByOrNull { it.length } ?: return null
        if (token.length < 2) return null

        val selection = "$music AND ${MediaStore.Audio.Media.TITLE} LIKE ?"
        val args = arrayOf("%$token%")

        var best: LocalTrack? = null
        var bestScore = 0.0
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val title = cursor.getString(titleCol) ?: continue
                val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" }
                val duration = cursor.getInt(durationCol).takeIf { it > 0 }
                val score = Matching.score(meta.title, meta.artist, meta.durationMs, title, artist, duration)
                if (score > bestScore) {
                    bestScore = score
                    val id = cursor.getLong(idCol)
                    best = LocalTrack(
                        id = id,
                        title = title,
                        artist = artist,
                        album = cursor.getString(albumCol),
                        durationMs = duration,
                        mimeType = cursor.getString(mimeCol),
                        uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    )
                }
            }
        }
        return if (bestScore >= threshold) best else null
    }

    fun search(context: Context, query: String, limit: Int = 40): List<LocalTrack> {
        if (!hasPermission(context) || query.isBlank()) return emptyList()
        val like = "%${query.trim()}%"
        val selection = "$music AND " +
            "(${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ? " +
            "OR ${MediaStore.Audio.Media.ALBUM} LIKE ?)"
        val out = mutableListOf<LocalTrack>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            arrayOf(like, like, like),
            "${MediaStore.Audio.Media.TITLE} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && out.size < limit) {
                out.add(read(cursor))
            }
        }
        return out
    }

    fun recent(context: Context, limit: Int = 30): List<LocalTrack> {
        if (!hasPermission(context)) return emptyList()
        val out = mutableListOf<LocalTrack>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            music,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext() && out.size < limit) {
                out.add(read(cursor))
            }
        }
        return out
    }

    fun formatBreakdown(context: Context): Map<String, Int> {
        if (!hasPermission(context)) return emptyMap()
        val counts = mutableMapOf<String, Int>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.MIME_TYPE),
            music,
            null,
            null,
        )?.use { cursor ->
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            while (cursor.moveToNext()) {
                val format = LocalTrack(0, "", null, null, null, cursor.getString(mimeCol), Uri.EMPTY).format
                counts[format] = (counts[format] ?: 0) + 1
            }
        }
        return counts.toList().sortedByDescending { it.second }.toMap()
    }

    private fun read(cursor: android.database.Cursor): LocalTrack {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
        return LocalTrack(
            id = id,
            title = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)) ?: "",
            artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST))
                ?.takeIf { it != "<unknown>" },
            album = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)),
            durationMs = cursor.getInt(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION))
                .takeIf { it > 0 },
            mimeType = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)),
            uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
        )
    }

    fun count(context: Context): Int {
        if (!hasPermission(context)) return 0
        return context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID),
            music,
            null,
            null,
        )?.use { it.count } ?: 0
    }
}
