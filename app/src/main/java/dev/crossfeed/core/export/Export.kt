package dev.crossfeed.core.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.Kind
import dev.crossfeed.core.history.Stats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Export(val uri: Uri, val name: String, val bytes: Long, val folder: String) {
    val path: String get() = "$folder/$name"
}

object Workbook {

    private const val MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

    suspend fun export(context: Context): Export = withContext(Dispatchers.IO) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val name = "crossfeed-listening-$stamp.xlsx"
        val sheets = sheets(context)
        val (uri, stream, folder) = target(context, name)
        stream.use { Xlsx.write(it, sheets) }
        Export(uri, name, size(context, uri), folder)
    }

    fun share(context: Context, export: Export) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MIME
            putExtra(Intent.EXTRA_STREAM, export.uri)
            putExtra(Intent.EXTRA_SUBJECT, export.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "send your listening data").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun sheets(context: Context): List<Sheet> {
        val db = HistoryDb.get(context)
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val plays = db.feed(limit = 100_000).map { play ->
            listOf(
                time.format(Date(play.startedAt)),
                play.title,
                play.artist,
                play.album,
                play.genre,
                play.source,
                play.listenedMs / 1000.0,
                play.durationMs / 1000.0,
                if (play.durationMs > 0) play.listenedMs.toDouble() / play.durationMs else null,
            )
        }
        val summary = db.summary("0")
        return listOf(
            Sheet(
                "plays",
                listOf("started", "title", "artist", "album", "genre", "app", "listened (s)", "length (s)", "finished"),
                plays,
            ),
            aggSheet(db, "by day", Kind.DAY, "day"),
            aggSheet(db, "tracks", Kind.TITLE, "track"),
            aggSheet(db, "artists", Kind.ARTIST, "artist"),
            aggSheet(db, "albums", Kind.ALBUM, "album"),
            aggSheet(db, "apps", Kind.SOURCE, "app"),
            aggSheet(db, "hours", Kind.HOUR, "hour"),
            aggSheet(db, "skips", Kind.SKIP, "track"),
            Sheet(
                "genres",
                listOf("genre", "plays", "listened (min)"),
                db.topGenres("0", limit = 500).map { listOf(it.label, it.plays, it.listenedMs / 60000.0) },
            ),
            Sheet(
                "finish rates",
                listOf("app", "finish rate", "tracks", "skips"),
                db.sourceList("0").map { source ->
                    val (rate, tracks, skips) = db.completion("0", source)
                    listOf(source, rate, tracks, skips)
                },
            ),
            Sheet(
                "summary",
                listOf("measure", "value"),
                listOf(
                    listOf("exported", time.format(Date())),
                    listOf("plays", summary.totalPlays),
                    listOf("minutes listened", summary.listenedMs / 60000.0),
                    listOf("distinct tracks", summary.distinctTracks),
                    listOf("distinct artists", summary.distinctArtists),
                    listOf("longest session (min)", db.meta("longest_run_ms") / 60000.0),
                    listOf("rows in feed", db.feedCount()),
                    listOf("database size", Stats.bytes(db.sizeBytes(context))),
                ),
            ),
        )
    }

    private fun aggSheet(db: HistoryDb, name: String, kind: String, label: String) = Sheet(
        name,
        listOf(label, "plays", "listened (min)"),
        db.top(kind, "0", limit = 5000).map { listOf(it.label, it.plays, it.listenedMs / 60000.0) },
    )

    private fun target(context: Context, name: String): Triple<Uri, OutputStream, String> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, MIME)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                val stream = resolver.openOutputStream(uri)
                if (stream != null) return Triple(uri, stream, Environment.DIRECTORY_DOWNLOADS)
            }
        }
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, name)
        return Triple(
            FileProvider.getUriForFile(context, "${context.packageName}.files", file),
            file.outputStream(),
            "crossfeed/exports",
        )
    }

    private fun size(context: Context, uri: Uri): Long =
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull() ?: 0L
}
