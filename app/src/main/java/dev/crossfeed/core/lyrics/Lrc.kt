package dev.crossfeed.core.lyrics

data class Line(val atMs: Long, val text: String)

data class Lyrics(
    val lines: List<Line>,
    val synced: Boolean,
    val instrumental: Boolean = false,
) {
    val empty: Boolean get() = lines.isEmpty() && !instrumental

    fun indexAt(positionMs: Long): Int {
        if (!synced || lines.isEmpty()) return -1
        var found = -1
        for ((index, line) in lines.withIndex()) {
            if (line.atMs <= positionMs) found = index else break
        }
        return found
    }
}

object Lrc {

    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(synced: String?, plain: String?, instrumental: Boolean): Lyrics {
        if (instrumental) return Lyrics(emptyList(), synced = false, instrumental = true)

        if (!synced.isNullOrBlank()) {
            val lines = mutableListOf<Line>()
            for (raw in synced.lines()) {
                val stamps = stamp.findAll(raw).toList()
                if (stamps.isEmpty()) continue
                val text = raw.substring(stamps.last().range.last + 1).trim()
                for (match in stamps) {
                    val (m, s, frac) = match.destructured
                    val fraction = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.take(3).toLong()
                    }
                    lines.add(Line(m.toLong() * 60_000 + s.toLong() * 1000 + fraction, text))
                }
            }
            if (lines.isNotEmpty()) {
                return Lyrics(lines.sortedBy { it.atMs }, synced = true)
            }
        }

        val flat = plain?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
        return Lyrics(flat.map { Line(0L, it) }, synced = false)
    }
}
