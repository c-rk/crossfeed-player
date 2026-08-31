package dev.crossfeed.core.export

import android.content.Context
import android.net.Uri
import dev.crossfeed.core.history.ArtStore
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.Play
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Restore {

    data class Result(val added: Int, val skipped: Int, val unreadable: Int, val sleeves: Int = 0) {
        val rows get() = added + skipped + unreadable
    }

    private const val NEAR_MS = 90_000L

    /** Files arrive without a name often enough to be worth asking the bytes instead. */
    private fun peeksAsZip(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val head = ByteArray(4)
            if (stream.read(head) < 4) return@use false
            // every zip starts PK\u0003\u0004, and an xlsx is a zip too, so the name decides
            // first and this is only the fallback when there is not one
            head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
        } ?: false
    }.getOrDefault(false)

    /**
     * Reads back either a bare spreadsheet or a whole bundle.
     *
     * A bundle carries its sleeves, so they are put back on disk first and the paths in the sheet
     * are pointed at where they now live. A bare sheet still works, it just arrives without
     * pictures, which is what it always did.
     */
    suspend fun fromFile(context: Context, uri: Uri, name: String?): Result = withContext(Dispatchers.IO) {
        val zipped = Bundle.looksLikeBundle(name) || peeksAsZip(context, uri)
        var sleeves = 0
        val rows = if (zipped) {
            val (sheet, saved) = Bundle.read(context, uri)
            sleeves = saved
            sheet
        } else {
            context.contentResolver.openInputStream(uri)
                ?.use { XlsxReader.sheet(it, "plays") }
                ?: throw IllegalArgumentException("cannot open that file")
        }
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
                    artwork = cell(row, artwork)?.let { landed(context, it) },
                ),
            )
            db.record(name, who, cell(row, album), app, startedAt, listenedMs, newPlay = true)
            db.recordFinish(name, app, startedAt, listenedMs, durationMs)
            kind?.let { db.setGenre(name, who, it) }
            added++
        }

        // a sheet lands on top of whatever was captured while it was away, so the two are
        // folded together before any of it is counted
        db.tidy()

        Result(added, skipped, unreadable, sleeves)
    }

    /** A sleeve carried in a bundle now lives in the art folder, so that is where it points. */
    private fun landed(context: Context, artwork: String): String {
        if (!artwork.startsWith(Bundle.ART)) return artwork
        val name = artwork.removePrefix(Bundle.ART)
        return "file://" + java.io.File(ArtStore.folder(context), name).absolutePath
    }

    /**
     * Puts a whole diary back, replacing the one here rather than merging into it.
     *
     * This is the answer to moving phones, where merging is the wrong verb: you do not want your
     * old diary folded into an empty one, you want it to be the diary. Everything the merge path
     * cannot carry comes with it, because it is the same file rather than a reading of it: the
     * saved tracks, the genre lookups, the artwork cache, what was already posted.
     *
     * The sleeves are unpacked first, since the diary that arrives will be naming them.
     */
    suspend fun whole(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val bytes = Bundle.database(context, uri)
            ?: throw IllegalArgumentException("that zip has no diary in it, only sheets")

        var sleeves = 0
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                java.util.zip.ZipInputStream(raw).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.name.startsWith(Bundle.ART) && !entry.isDirectory) {
                            val name = entry.name.removePrefix(Bundle.ART)
                            if (ArtStore.accept(context, name, zip.readBytes()) != null) sleeves++
                        }
                        zip.closeEntry()
                    }
                }
            }
        }

        if (!HistoryDb.replace(context, bytes)) {
            throw IllegalArgumentException("that diary would not open, so nothing was changed")
        }
        val plays = HistoryDb.get(context).feed(limit = 1_000_000).size
        Result(added = plays, skipped = 0, unreadable = 0, sleeves = sleeves)
    }

    fun describe(result: Result): String = buildString {
        append("brought back ${result.added} ${if (result.added == 1) "play" else "plays"}")
        if (result.skipped > 0) append(", skipped ${result.skipped} already here")
        if (result.unreadable > 0) append(", ${result.unreadable} unreadable")
        if (result.sleeves > 0) append(", ${result.sleeves} sleeves")
    }
}
