package dev.crossfeed.core.export

import android.content.Context
import android.net.Uri
import dev.crossfeed.core.history.ArtStore
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The whole diary in one file: the spreadsheet, and the sleeves it names.
 *
 * A spreadsheet on its own is honest but incomplete. Its artwork column points at paths on the
 * phone that wrote it, so a diary carried to another phone arrives with every square blank. The
 * sleeves are already sitting in the app's own folder as small webp files, so the export puts
 * them in beside the sheet and rewrites the column to point inside the zip.
 *
 * Opened anywhere else it is still just a zip: a spreadsheet you can double click and a folder of
 * pictures. Nothing here needs this app to read it.
 */
object Bundle {

    const val MIME = "application/zip"
    const val SHEET = "listening.xlsx"
    const val ART = "art/"
    const val DB = "listening.db"

    /** Writes the sheet and every sleeve it refers to into one archive. */
    fun write(context: Context, out: OutputStream, sheets: List<Sheet>): Int {
        val wanted = mutableSetOf<String>()
        val rewritten = sheets.map { sheet ->
            val column = sheet.columns.indexOf("artwork")
            if (sheet.name != "plays" || column < 0) return@map sheet
            sheet.copy(
                rows = sheet.rows.map { row ->
                    val cell = row.getOrNull(column)?.toString()
                    val name = ArtStore.nameOf(cell)
                    if (name == null) {
                        row
                    } else {
                        wanted.add(name)
                        row.toMutableList().also { it[column] = ART + name }
                    }
                },
            )
        }

        var sleeves = 0
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(SHEET))
            Xlsx.write(NonClosing(zip), rewritten)
            zip.closeEntry()

            // the diary itself, so an import can be a restore rather than a merge. it is the
            // same data the sheet holds, in the form the app actually reads
            val database = dev.crossfeed.core.history.HistoryDb.file(context)
            if (database.exists()) {
                dev.crossfeed.core.history.HistoryDb.get(context).checkpoint()
                zip.putNextEntry(ZipEntry(DB))
                database.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }

            for (file in ArtStore.files(context)) {
                if (file.name !in wanted) continue
                zip.putNextEntry(ZipEntry(ART + file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                sleeves++
            }
        }
        return sleeves
    }

    /**
     * Reads an archive back: the sleeves go to the art folder first, so that by the time the
     * sheet is read every path it names already exists.
     */
    fun read(context: Context, uri: Uri): Pair<List<List<String?>>, Int> {
        var sheet: ByteArray? = null
        var sleeves = 0

        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.name == SHEET -> sheet = zip.readBytes()
                        entry.name.startsWith(ART) && !entry.isDirectory -> {
                            val name = entry.name.removePrefix(ART)
                            if (ArtStore.accept(context, name, zip.readBytes()) != null) sleeves++
                        }
                    }
                    zip.closeEntry()
                }
            }
        }

        val bytes = sheet ?: throw IllegalArgumentException("no spreadsheet inside that zip")
        val rows = bytes.inputStream().use { XlsxReader.sheet(it, "plays") }
        return rows to sleeves
    }

    /** Pulls just the diary out of an archive, for a restore rather than a merge. */
    fun database(context: Context, uri: Uri): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.name == DB) return zip.readBytes()
                    zip.closeEntry()
                }
            }
        }
        return null
    }

    /**
     * Whether this is one of ours, decided by looking inside rather than at the name.
     *
     * A spreadsheet is a zip too, so neither the extension nor the first four bytes settle it. The
     * only honest answer comes from the entry names: ours holds a diary, a sheet under a name we
     * chose, or a folder of sleeves. Anything else is a plain spreadsheet and is read as one.
     */
    fun isBundle(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { raw ->
            ZipInputStream(raw).use { zip ->
                var seen = 0
                while (seen < 40) {
                    val entry = zip.nextEntry ?: break
                    seen++
                    val name = entry.name
                    if (name == DB || name == SHEET || name.startsWith(ART)) return@use true
                    zip.closeEntry()
                }
                false
            }
        } ?: false
    }.getOrDefault(false)

    /** The zip stream must outlive each entry, so the writer inside it may not close it. */
    private class NonClosing(private val inner: OutputStream) : OutputStream() {
        override fun write(byte: Int) = inner.write(byte)
        override fun write(bytes: ByteArray, off: Int, len: Int) = inner.write(bytes, off, len)
        override fun flush() = inner.flush()
        override fun close() = flush()
    }
}
