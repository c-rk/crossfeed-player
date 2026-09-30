package dev.crossfeed.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.crossfeed.core.Router
import dev.crossfeed.core.history.Days
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.Moods
import dev.crossfeed.core.history.Stats
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Bumped whenever a mood is noted, so the weather redraws without being asked. */
object Feelings {
    var version by mutableIntStateOf(0)
        private set

    fun changed() {
        version++
    }
}

private fun moodColor(name: String?): Color? = Moods.of(name)?.let { Color(it.argb) }

/**
 * How it feels right now, in one tap.
 *
 * It is noted against whatever is playing at that moment, if anything is, which is what lets the
 * weather below say not only how much you listened but how it felt. It never leaves the phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodCard(deck: Deck?) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf<HistoryDb.Mood?>(null) }

    LaunchedEffect(Unit) {
        last = withContext(Dispatchers.IO) { runCatching { HistoryDb.get(context).lastMood() }.getOrNull() }
    }

    val today = last?.takeIf { Days.of(it.at) == Days.of(System.currentTimeMillis()) }
    // a tap soon after the last one corrects it, so the chip stays lit for as long as that holds
    val fresh = last?.takeIf { System.currentTimeMillis() - it.at < 10 * 60_000L }

    GlassCard(padding = Space.medium) {
        Text("how does it feel", style = Type.section, color = glass.t1)
        Text(
            today?.let { mood ->
                "noted " + mood.mood + (mood.title?.let { " with $it" } ?: "") + ", " + clock(mood.at)
            } ?: "a tap notes it against whatever is playing. it stays on this phone.",
            style = Type.note,
            color = glass.t3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, bottom = Space.small),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
            verticalArrangement = Arrangement.spacedBy(Space.tight),
        ) {
            for (mood in Moods.all) {
                val tint = Color(mood.argb)
                val on = fresh?.mood == mood.name
                Row(
                    Modifier
                        .clip(Shapes.chip)
                        .background(if (on) tint.copy(alpha = 0.26f) else glass.p1)
                        .border(1.dp, if (on) tint else glass.bd, Shapes.chip)
                        .clickable {
                            val playing = deck?.takeIf { it.playing }
                            scope.launch {
                                last = withContext(Dispatchers.IO) {
                                    HistoryDb.get(context).noteMood(mood.name, playing?.title, playing?.artist)
                                }
                                Feelings.changed()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Spacer(Modifier.size(8.dp).clip(CircleShape).background(tint))
                    Text(mood.name, style = Type.metaStrong, color = if (on) glass.t1 else glass.t2)
                }
            }
        }
    }
}

/**
 * What you had on repeat this week, some time ago. It reaches as far back as the diary goes.
 */
@Composable
fun CapsuleCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    var capsule by remember { mutableStateOf<HistoryDb.Capsule?>(null) }

    LaunchedEffect(Unit) {
        capsule = withContext(Dispatchers.IO) { runCatching { HistoryDb.get(context).capsule() }.getOrNull() }
    }

    val found = capsule ?: return
    Spacer(Modifier.height(Space.small))
    GlassCard(
        padding = Space.medium,
        modifier = Modifier.clickable { scope.launch { Router.play(context, found.title, found.artist) } },
    ) {
        Text(ago(found.daysAgo), style = Type.tag, color = glass.accent)
        Spacer(Modifier.height(Space.small))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Sleeve(found.title, found.artwork, 52.dp, Shapes.artSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(found.title, style = Type.rowTitleLarge, color = glass.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                found.artist?.let {
                    Text(it, style = Type.note, color = glass.t3, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "on repeat: ${found.plays} ${if (found.plays == 1) "play" else "plays"}, " + Stats.minutes(found.listenedMs),
                    style = Type.stamp,
                    color = glass.t3,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Text(
            "that week you listened for " + Stats.minutes(found.weekMs) + ". tap to hear it again.",
            style = Type.note,
            color = glass.t3,
            modifier = Modifier.padding(top = Space.small),
        )
    }
}

private fun ago(days: Int): String = when {
    days >= 365 -> "a year ago this week"
    days >= 180 -> "six months ago this week"
    days >= 30 -> "a month ago this week"
    else -> "a week ago today"
}

private val WEEKS = 53

/**
 * A year of days, a square each. A day with a mood noted wears that mood; a day without one is
 * grey, darker the longer you listened. Tapping a square says what that day was.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun WeatherCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val version = Feelings.version

    val start = remember { firstMonday() }
    var days by remember { mutableStateOf<Map<String, HistoryDb.Weather>>(emptyMap()) }
    var picked by remember { mutableStateOf(Days.of(System.currentTimeMillis())) }
    var top by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(version) {
        days = withContext(Dispatchers.IO) {
            runCatching { HistoryDb.get(context).weather(Days.of(start.timeInMillis)) }.getOrDefault(emptyMap())
        }
    }
    LaunchedEffect(picked, version) {
        top = withContext(Dispatchers.IO) { runCatching { HistoryDb.get(context).topOfDay(picked) }.getOrNull()?.label }
    }

    val today = Days.of(System.currentTimeMillis())
    val peak = days.values.maxOfOrNull { it.listenedMs }?.coerceAtLeast(1L) ?: 1L
    val grid = remember(start) { (0 until WEEKS * 7).map { dayAt(start, it) } }
    val felt = Moods.all.filter { mood -> days.values.any { it.mood == mood.name } }

    Spacer(Modifier.height(Space.small))
    GlassCard(padding = Space.medium) {
        Text("the weather", style = Type.section, color = glass.t1)
        Text(
            "a year of listening, a square a day. a noted mood colours its day.",
            style = Type.note,
            color = glass.t3,
            modifier = Modifier.padding(top = 2.dp, bottom = Space.small),
        )
        val chosen = days[picked]
        Text(
            listOfNotNull(
                pretty(picked) + if (picked == today) ", so far" else "",
                chosen?.listenedMs?.takeIf { it > 0 }?.let { Stats.minutes(it) } ?: "nothing played",
                chosen?.mood?.let { mood -> mood + if (chosen.moods > 1) " (${chosen.moods} noted)" else "" },
                top?.let { "most: $it" },
            ).joinToString(" · "),
            style = Type.stamp,
            color = glass.t2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val gap = with(density) { 2.dp.toPx() }
            val cell = (with(density) { maxWidth.toPx() } - gap * (WEEKS - 1)) / WEEKS
            val height = with(density) { (cell * 7 + gap * 6).toDp() }
            val empty = glass.t1.copy(alpha = 0.06f)
            val ink = glass.t1
            val ring = glass.t1
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .pointerInput(grid) {
                        detectTapGestures { at ->
                            val column = (at.x / (cell + gap)).toInt().coerceIn(0, WEEKS - 1)
                            val row = (at.y / (cell + gap)).toInt().coerceIn(0, 6)
                            val day = grid[column * 7 + row]
                            if (day <= today) picked = day
                        }
                    },
            ) {
                val radius = CornerRadius(cell * 0.22f)
                grid.forEachIndexed { index, day ->
                    if (day > today) return@forEachIndexed
                    val weather = days[day]
                    val color = moodColor(weather?.mood)
                        ?: weather?.listenedMs?.takeIf { it > 0 }
                            ?.let { ink.copy(alpha = 0.12f + 0.5f * (it.toFloat() / peak)) }
                        ?: empty
                    val topLeft = Offset((index / 7) * (cell + gap), (index % 7) * (cell + gap))
                    drawRoundRect(color, topLeft, Size(cell, cell), radius)
                    if (day == picked) {
                        drawRoundRect(ring, topLeft, Size(cell, cell), radius, style = Stroke(width = gap))
                    }
                }
            }
        }

        if (felt.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = Space.small),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (mood in felt) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.size(7.dp).clip(CircleShape).background(Color(mood.argb)))
                        Spacer(Modifier.width(5.dp))
                        Text(mood.name, style = Type.stamp, color = glass.t3)
                    }
                }
            }
        }
    }
}

/** The monday that starts the fifty three weeks ending with this one. */
private fun firstMonday(): Calendar = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 12)
    set(Calendar.MINUTE, 0)
    val back = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    add(Calendar.DAY_OF_YEAR, -back - (WEEKS - 1) * 7)
}

private fun dayAt(start: Calendar, offset: Int): String =
    Days.of((start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }.timeInMillis)

private fun pretty(day: String): String = runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)!!
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(date).lowercase()
}.getOrDefault(day)

private fun clock(at: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
