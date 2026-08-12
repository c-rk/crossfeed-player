package dev.crossfeed.core.export

import android.content.Context
import android.net.Uri
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.Play
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Restore {

    data class Result(val added: Int, val skipped: Int, val unreadable: Int) {
        val rows get() = added + skipped + unreadable
    }

    private const val NEAR_MS = 90_000L

    suspend fun fromXlsx(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val rows = context.contentResolver.openInputStream(uri)
            ?.use { XlsxReader.sheet(it, "plays") }
            ?: throw IllegalArgumentException("cannot open that file")
        if (rows.size < 2) throw IllegalArgumentException("no plays sheet in that file")

        val header = rows.first().map { it?.trim()?.lowercase().orEmpty() }
        fun column(vararg names: String): Int =
            names.firstNotNullOfOrNull { name -> header.indexOf(name).takeIf { it >= 0 } } ?: -1

        val started = column("started")
        val title = column("title")
        val artist = column("artist")
        val album = column("album")
        val genre = column("genre")
        val source = column("app", "source")
        val listened = column("listened (s)", "listened")
        val length = column("length (s)", "length")
        val artwork = column("artwork")
        if (started < 0 || title < 0 || source < 0) {
            throw IllegalArgumentException("that sheet is missing started, title or app")
        }

        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        val db = HistoryDb.get(context)
        var added = 0
        var skipped = 0
        var unreadable = 0

        fun cell(row: List<String?>, index: Int): String? =
            if (index in row.indices) row[index]?.trim()?.takeIf { it.isNotEmpty() } else null

        for (row in rows.drop(1).asReversed()) {
            val name = cell(row, title)
            val app = cell(row, source)
            val at = cell(row, started)?.let { runCatching { stamp.parse(it) }.getOrNull() }
            if (name == null || app == null || at == null) {
                unreadable++
                continue
            }
            val who = cell(row, artist)
            val startedAt = at.time
            if (db.existsAround(name, who, app, startedAt - NEAR_MS, startedAt + NEAR_MS)) {
                skipped++
                continue
            }
            val listenedMs = ((cell(row, listened)?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
            val durationMs = ((cell(row, length)?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
            val kind = cell(row, genre)
            db.insert(
                Play(
                    id = 0,
                    title = name,
                    artist = who,
                    album = cell(row, album),
                    durationMs = durationMs,
                    listenedMs = listenedMs,
                    source = app,
                    startedAt = startedAt,
                    genre = kind,
                    artwork = cell(row, artwork),
                ),
            )
            db.record(name, who, cell(row, album), app, startedAt, listenedMs, newPlay = true)
            db.recordFinish(name, app, startedAt, listenedMs, durationMs)
            kind?.let { db.setGenre(name, who, it) }
            added++
        }

        Result(added, skipped, unreadable)
    }

    fun describe(result: Result): String = buildString {
        append("brought back ${result.added} ${if (result.added == 1) "play" else "plays"}")
        if (result.skipped > 0) append(", skipped ${result.skipped} already here")
        if (result.unreadable > 0) append(", ${result.unreadable} unreadable")
    }
}
