package dev.crossfeed.core.export

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class Sheet(val name: String, val columns: List<String>, val rows: List<List<Any?>>)

object Xlsx {

    fun write(out: OutputStream, sheets: List<Sheet>) {
        ZipOutputStream(out).use { zip ->
            zip.put("[Content_Types].xml", contentTypes(sheets.size))
            zip.put("_rels/.rels", RELS)
            zip.put("xl/workbook.xml", workbook(sheets))
            zip.put("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            sheets.forEachIndexed { index, sheet ->
                zip.put("xl/worksheets/sheet${index + 1}.xml", sheet(sheet))
            }
        }
    }

    private fun ZipOutputStream.put(path: String, body: String) {
        putNextEntry(ZipEntry(path))
        write(body.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun contentTypes(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append(
            """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""",
        )
        for (index in 1..count) {
            append(
                """<Override PartName="/xl/worksheets/sheet$index.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""",
            )
        }
        append("</Types>")
    }

    private const val RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun workbook(sheets: List<Sheet>) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """)
        append("""xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""")
        sheets.forEachIndexed { index, sheet ->
            append("""<sheet name="${escape(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (index in 1..count) {
            append(
                """<Relationship Id="rId$index" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$index.xml"/>""",
            )
        }
        append("</Relationships>")
    }

    private fun sheet(sheet: Sheet) = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        append(row(1, sheet.columns))
        sheet.rows.forEachIndexed { index, values -> append(row(index + 2, values)) }
        append("</sheetData></worksheet>")
    }

    private fun row(number: Int, values: List<Any?>) = buildString {
        append("""<row r="$number">""")
        values.forEachIndexed { index, value ->
            val ref = "${column(index)}$number"
            when (value) {
                null -> Unit
                is Number -> append("""<c r="$ref"><v>$value</v></c>""")
                else -> append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escape(value.toString())}</t></is></c>""")
            }
        }
        append("</row>")
    }

    private fun column(index: Int): String {
        var value = index
        val out = StringBuilder()
        while (true) {
            out.insert(0, ('A' + value % 26))
            value = value / 26 - 1
            if (value < 0) break
        }
        return out.toString()
    }

    private fun escape(text: String) = buildString(text.length) {
        for (char in text) {
            when {
                char == '&' -> append("&amp;")
                char == '<' -> append("&lt;")
                char == '>' -> append("&gt;")
                char == '"' -> append("&quot;")
                char.code < 0x20 && char != '\n' && char != '\t' -> append(' ')
                else -> append(char)
            }
        }
    }
}
