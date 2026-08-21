package dev.crossfeed.core.export

import android.util.Xml
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

object XlsxReader {

    fun sheet(input: InputStream, name: String): List<List<String?>> {
        val parts = unzip(input)
        val path = locate(parts, name) ?: return emptyList()
        val body = parts[path] ?: return emptyList()
        return rows(body)
    }

    /**
     * A spreadsheet arrives from wherever the user found it, and a small file can unpack into a
     * very large one, so the archive is read up to a ceiling rather than to its own claim.
     */
    private fun unzip(input: InputStream): Map<String, ByteArray> {
        val parts = mutableMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (parts.size >= MAX_ENTRIES) break
                if (!entry.isDirectory) {
                    val bytes = zip.readBytes()
                    total += bytes.size
                    if (total > MAX_BYTES) throw IllegalArgumentException("that file is too large")
                    parts[entry.name] = bytes
                }
                zip.closeEntry()
            }
        }
        return parts
    }

    private const val MAX_BYTES = 64L * 1024 * 1024
    private const val MAX_ENTRIES = 512
    private const val MAX_COLUMN = 16_383

    private fun locate(parts: Map<String, ByteArray>, name: String): String? {
        val workbook = parts["xl/workbook.xml"]?.toString(Charsets.UTF_8) ?: return null
        val entry = Regex("""<sheet[^>]*name="([^"]*)"[^>]*r:id="([^"]*)"[^>]*/>""")
            .findAll(workbook)
            .firstOrNull { unescape(it.groupValues[1]).equals(name, ignoreCase = true) }
            ?: return null
        val rels = parts["xl/_rels/workbook.xml.rels"]?.toString(Charsets.UTF_8)
        val target = rels?.let {
            Regex("""<Relationship[^>]*Id="${Regex.escape(entry.groupValues[2])}"[^>]*Target="([^"]*)"""")
                .find(it)?.groupValues?.get(1)
        }
        val path = "xl/" + (target ?: "worksheets/sheet1.xml").removePrefix("/").removePrefix("xl/")
        return if (parts.containsKey(path)) path else parts.keys.firstOrNull { it.startsWith("xl/worksheets/") }
    }

    private fun rows(body: ByteArray): List<List<String?>> {
        val out = mutableListOf<List<String?>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(body), null)

        var row: MutableList<String?>? = null
        var column = 0
        var inline = false
        var numeric = false
        var text: String? = null

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = mutableListOf()
                    "c" -> {
                        column = columnOf(parser.getAttributeValue(null, "r"))
                        inline = parser.getAttributeValue(null, "t") == "inlineStr"
                        numeric = !inline
                        text = null
                    }
                    "t" -> if (inline) text = ""
                    "v" -> if (numeric) text = ""
                }

                XmlPullParser.TEXT -> if (text != null) text += parser.text

                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> row?.let { cells ->
                        while (cells.size < column) cells.add(null)
                        if (cells.size == column) cells.add(text) else cells[column] = text
                    }
                    "row" -> row?.let { out.add(it); row = null }
                }
            }
        }
        return out
    }

    private fun columnOf(reference: String?): Int {
        if (reference.isNullOrBlank()) return 0
        var value = 0
        for (char in reference) {
            if (!char.isLetter()) break
            value = value * 26 + (char.uppercaseChar() - 'A' + 1)
        }
        return (value - 1).coerceIn(0, MAX_COLUMN)
    }

    private fun unescape(text: String) = text
        .replace("&quot;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}
