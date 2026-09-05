package dev.crossfeed.core

import org.json.JSONArray
import org.json.JSONObject

object Html {

    // a page is scraped for a handful of short tags near the top, so there is nothing to gain from
    // reading further. the bound matters because an unclosed tag makes every `[^>]` run walk to the
    // end of the document and backtrack from there, once per starting position
    private const val LIMIT = 200_000

    // and the runs themselves are bounded, so a position that cannot match fails after a few
    // hundred characters rather than after the rest of the page
    private const val ATTRS = 500

    private val jsonLd = Regex(
        """<script[^>]{1,$ATTRS}type=["']application/ld\+json["'][^>]{0,$ATTRS}>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private val titleTag = Regex(
        """<title[^>]{0,$ATTRS}>([\s\S]{0,4000}?)</title>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private fun clip(html: String): String = if (html.length > LIMIT) html.take(LIMIT) else html

    fun meta(html: String, key: String): String? {
        val escaped = Regex.escape(key)
        val forward = Regex(
            """<meta[^>]{1,$ATTRS}(?:property|name)=["']$escaped["'][^>]{0,$ATTRS}content=["']([^"']{0,4000})["']""",
            RegexOption.IGNORE_CASE,
        )
        val reverse = Regex(
            """<meta[^>]{1,$ATTRS}content=["']([^"']{0,4000})["'][^>]{0,$ATTRS}(?:property|name)=["']$escaped["']""",
            RegexOption.IGNORE_CASE,
        )
        val text = clip(html)
        val raw = forward.find(text)?.groupValues?.get(1) ?: reverse.find(text)?.groupValues?.get(1)
        return raw?.let { unescape(it) }?.takeIf { it.isNotBlank() }
    }

    fun title(html: String): String? =
        titleTag.find(clip(html))?.groupValues?.get(1)?.let { unescape(it).trim() }?.takeIf { it.isNotBlank() }

    fun jsonLdObjects(html: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        for (match in jsonLd.findAll(clip(html))) {
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
