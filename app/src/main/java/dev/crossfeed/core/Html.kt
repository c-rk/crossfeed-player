package dev.crossfeed.core

import org.json.JSONArray
import org.json.JSONObject

object Html {

    private val jsonLd = Regex(
        """<script[^>]+type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private val titleTag = Regex("""<title[^>]*>(.*?)</title>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))

    fun meta(html: String, key: String): String? {
        val escaped = Regex.escape(key)
        val forward = Regex(
            """<meta[^>]+(?:property|name)=["']$escaped["'][^>]*content=["']([^"']*)["']""",
            RegexOption.IGNORE_CASE,
        )
        val reverse = Regex(
            """<meta[^>]+content=["']([^"']*)["'][^>]*(?:property|name)=["']$escaped["']""",
            RegexOption.IGNORE_CASE,
        )
        val raw = forward.find(html)?.groupValues?.get(1) ?: reverse.find(html)?.groupValues?.get(1)
        return raw?.let { unescape(it) }?.takeIf { it.isNotBlank() }
    }

    fun title(html: String): String? =
        titleTag.find(html)?.groupValues?.get(1)?.let { unescape(it).trim() }?.takeIf { it.isNotBlank() }

    fun jsonLdObjects(html: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        for (match in jsonLd.findAll(html)) {
            val text = match.groupValues[1].trim()
            runCatching {
                when {
                    text.startsWith("[") -> {
                        val arr = JSONArray(text)
                        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(out::add)
                    }

                    text.startsWith("{") -> out.add(JSONObject(text))
                }
            }
        }
        return out
    }

    fun unescape(s: String): String = s
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&#x27;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace("\\u0026", "&")

    fun isoDurationMs(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val m = Regex("""P(?:(\d+)D)?T?(?:(\d+)H)?(?:(\d+)M)?(?:([\d.]+)S)?""").find(value) ?: return null
        val (d, h, min, sec) = m.destructured
        val total = (d.toIntOrNull() ?: 0) * 86400 +
            (h.toIntOrNull() ?: 0) * 3600 +
            (min.toIntOrNull() ?: 0) * 60 +
            (sec.toDoubleOrNull()?.toInt() ?: 0)
        return if (total > 0) total * 1000 else null
    }
}
