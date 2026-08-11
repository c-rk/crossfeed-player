package dev.crossfeed.core

import java.text.Normalizer
import kotlin.math.abs

object Matching {

    private val marks = Regex("""\p{Mn}+""")
    private val featuring = Regex("""\s*[(\[]?\s*(feat\.?|featuring|ft\.?|with)\s+[^)\]]*[)\]]?""", RegexOption.IGNORE_CASE)
    private val noise = Regex(
        """\s*[(\[][^)\]]*\b(remaster(ed)?|deluxe|expanded|bonus track|explicit|clean|single version|album version|radio edit|original mix|official\s+(music\s+)?(video|audio)|lyrics?(\s+video)?|visuali[sz]er|music\s+video|4k|hd)\b[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE,
    )
    private val punctuation = Regex("""[^\p{L}\p{N}\s]""")
    private val spaces = Regex("""\s+""")

    fun norm(value: String?): String {
        if (value.isNullOrBlank()) return ""
        var s = Normalizer.normalize(value, Normalizer.Form.NFD)
        s = marks.replace(s, "")
        s = s.lowercase()
        s = noise.replace(s, " ")
        s = featuring.replace(s, " ")
        s = punctuation.replace(s, " ")
        return spaces.replace(s, " ").trim()
    }

    private fun tokens(value: String?): Set<String> =
        norm(value).split(' ').filter { it.isNotBlank() }.toSet()

    fun similarity(a: String?, b: String?): Double {
        val left = tokens(a)
        val right = tokens(b)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 1.0
        val intersection = left.intersect(right).size.toDouble()
        val union = left.union(right).size.toDouble()
        val jaccard = intersection / union
        val containment = intersection / minOf(left.size, right.size).toDouble()
        return (jaccard * 0.5) + (containment * 0.5)
    }

    fun score(
        wantTitle: String?,
        wantArtist: String?,
        wantDurationMs: Int?,
        gotTitle: String?,
        gotArtist: String?,
        gotDurationMs: Int?,
    ): Double {
        val title = similarity(wantTitle, gotTitle)
        val artist = if (wantArtist.isNullOrBlank()) 0.5 else similarity(wantArtist, gotArtist)
        val duration = if (wantDurationMs == null || gotDurationMs == null) {
            0.5
        } else {
            val delta = abs(wantDurationMs - gotDurationMs)
            when {
                delta <= 2000 -> 1.0
                delta <= 5000 -> 0.7
                delta <= 15000 -> 0.3
                else -> 0.0
            }
        }
        return (title * 0.6) + (artist * 0.3) + (duration * 0.1)
    }
}
