package dev.crossfeed.core.history

import android.database.sqlite.SQLiteDatabase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Kind {
    const val ARTIST = "artist"
    const val TITLE = "title"
    const val ALBUM = "album"
    const val SOURCE = "source"
    const val HOUR = "hour"
    const val DAY = "day"
    const val SKIP = "skip"
}

data class Habits(
    val streak: Int,
    val peakHour: Int?,
    val completion: Double,
    val skips: Int,
)

data class Advanced(
    val longestSessionMs: Long,
    val mostSkipped: Tally?,
    val perDayMs: Long,
    val biggestDay: Pair<String, Long>?,
    val newArtists: Int,
    val newTracks: Int,
    val obsession: Triple<String, String, Int>?,
    val weekdayMs: Long,
    val weekendMs: Long,
    val bySource: List<Triple<String, Double, Int>>,
    val activeDays: Int,
)

object Days {
    private val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun of(millis: Long): String = format.format(java.util.Date(millis))

    fun hourOf(millis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return calendar.get(Calendar.HOUR_OF_DAY)
    }

    fun isWeekend(day: String): Boolean = runCatching {
        val calendar = Calendar.getInstance().apply { time = format.parse(day)!! }
        val field = calendar.get(Calendar.DAY_OF_WEEK)
        field == Calendar.SATURDAY || field == Calendar.SUNDAY
    }.getOrDefault(false)

    fun ago(days: Int): String {
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }
        return format.format(calendar.time)
    }

    fun today(): String = of(System.currentTimeMillis())
}

internal fun SQLiteDatabase.bump(kind: String, label: String, day: String, plays: Int, listenedMs: Long) {
    if (label.isBlank()) return
    execSQL(
        "INSERT OR IGNORE INTO agg(kind,label,day,plays,listened_ms) VALUES(?,?,?,0,0)",
        arrayOf(kind, label, day),
    )
    execSQL(
        "UPDATE agg SET plays = plays + ?, listened_ms = listened_ms + ? WHERE kind=? AND label=? AND day=?",
        arrayOf(plays, listenedMs, kind, label, day),
    )
}

internal fun SQLiteDatabase.bumpFinish(source: String, day: String, finished: Double, skip: Boolean) {
    execSQL(
        "INSERT OR IGNORE INTO finishes(source,day,finished_sum,tracks,skips) VALUES(?,?,0,0,0)",
        arrayOf(source, day),
    )
    execSQL(
        "UPDATE finishes SET finished_sum = finished_sum + ?, tracks = tracks + 1, skips = skips + ? " +
            "WHERE source=? AND day=?",
        arrayOf(finished, if (skip) 1 else 0, source, day),
    )
}
