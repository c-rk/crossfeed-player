package dev.crossfeed.core.history

import android.content.Context
import dev.crossfeed.core.AppleCatalog
import dev.crossfeed.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Range(val label: String, val days: Int) {
    TODAY("today", 0),
    WEEK("week", 6),
    MONTH("month", 29),
    ALL("all time", -1);

    fun sinceDay(): String = if (days < 0) "0" else Days.ago(days)

    fun sinceMillis(): Long =
        if (days < 0) 0L else System.currentTimeMillis() - (days + 1) * 86_400_000L
}

data class Dashboard(
    val summary: Summary,
    val artists: List<Tally>,
    val tracks: List<Tally>,
    val albums: List<Tally>,
    val genres: List<Tally>,
    val sources: List<Tally>,
    val feed: List<Play>,
    val habits: Habits,
    val advanced: Advanced,
)

object Stats {

    suspend fun load(context: Context, range: Range, query: String): Dashboard = withContext(Dispatchers.IO) {
        val db = HistoryDb.get(context)
        val sinceDay = range.sinceDay()
        val (completion, _, skips) = db.completion(sinceDay)
        val days = db.activeDays(sinceDay)
        val listened = days.sumOf { it.second }

        Dashboard(
            summary = db.summary(sinceDay),
            artists = db.top(Kind.ARTIST, sinceDay),
            tracks = db.top(Kind.TITLE, sinceDay),
            albums = db.top(Kind.ALBUM, sinceDay),
            genres = db.topGenres(sinceDay),
            sources = db.top(Kind.SOURCE, sinceDay, limit = 6),
            feed = db.feed(range.sinceMillis(), query),
            habits = Habits(
                streak = streak(db.activeDays().map { it.first }),
                peakHour = db.hourHistogram(sinceDay).maxByOrNull { it.second }?.first,
                completion = completion,
                skips = skips,
            ),
            advanced = Advanced(
                longestSessionMs = db.meta("longest_run_ms"),
                mostSkipped = db.topByPlays(Kind.SKIP, sinceDay).firstOrNull(),
                perDayMs = if (days.isNotEmpty()) listened / days.size else 0,
                biggestDay = days.maxByOrNull { it.second },
                newArtists = db.firstSeenCount(Kind.ARTIST, sinceDay),
                newTracks = db.firstSeenCount(Kind.TITLE, sinceDay),
                obsession = db.obsession(sinceDay),
                weekdayMs = days.filterNot { Days.isWeekend(it.first) }.sumOf { it.second },
                weekendMs = days.filter { Days.isWeekend(it.first) }.sumOf { it.second },
                bySource = db.sourceList(sinceDay).map { source ->
                    val (rate, _, sourceSkips) = db.completion(sinceDay, source)
                    Triple(source, rate, sourceSkips)
                },
                activeDays = days.size,
            ),
        )
    }

    suspend fun enrichGenres(context: Context) = withContext(Dispatchers.IO) {
        if (!Prefs(context).lookUpGenres) return@withContext
        val db = HistoryDb.get(context)
        val country = Prefs(context).country
        for ((title, artist) in db.needsGenre()) {
            val query = listOfNotNull(artist, title).joinToString(" ")
            val genre = AppleCatalog.search(query, country, limit = 1).firstOrNull()?.genre ?: continue
            db.setGenre(title, artist, genre)
        }
    }

    private fun streak(days: List<String>): Int {
        if (days.isEmpty()) return 0
        var count = 0
        var cursor = 0
        while (cursor < days.size && days[cursor] == Days.ago(count)) {
            count++
            cursor++
        }
        return count
    }

    fun hourLabel(hour: Int): String = when {
        hour == 0 -> "12am"
        hour < 12 -> "${hour}am"
        hour == 12 -> "12pm"
        else -> "${hour - 12}pm"
    }

    fun minutes(ms: Long): String {
        val total = ms / 60_000
        return when {
            total >= 60 -> "${total / 60}h ${total % 60}m"
            total >= 1 -> "${total}m"
            ms <= 0 -> "0m"
            else -> "${ms / 1000}s"
        }
    }

    fun clock(ms: Long): String {
        val seconds = ms / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }

    fun bytes(size: Long): String = when {
        size >= 1024 * 1024 -> "%.1f MB".format(size / 1024.0 / 1024.0)
        size >= 1024 -> "%.0f KB".format(size / 1024.0)
        else -> "$size B"
    }
}
