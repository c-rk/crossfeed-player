package dev.crossfeed.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.crossfeed.core.history.Days
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.Moods
import dev.crossfeed.core.history.Stats
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.hypot

/** Where the weather is looking: every day, one month, one week, or one day by the hour. */
sealed interface Zoom {
    data object All : Zoom
    data class Month(val first: String) : Zoom
    data class Week(val monday: String) : Zoom
    data class Day(val day: String) : Zoom
}

/** One box, placed in a unit square so any layout can be drawn at any size and moved between. */
private data class Box(
    val key: String,
    val rect: Rect,
    val color: Color,
    val label: String? = null,
    val sub: String? = null,
    val day: String? = null,
    val hour: Int? = null,
)

/**
 * Every day the diary has, as one square that keeps dividing, and every period inside it.
 *
 * The first day is the whole square. Each time it outgrows itself the whole thing shrinks into
 * its top left quarter and the new days fill the other three, a Z within a Z, which is what lets
 * the old square survive intact inside the new one. That division plays through when the card
 * comes into view.
 *
 * A tap goes one level in: a day's month as a calendar, then its week as seven columns, then the
 * day as its twenty four hours. The boxes travel between layouts rather than being redrawn, so a
 * day visibly moves from the square into its place on the calendar. A pinch goes out or in a
 * level, and the trail above the square jumps back to any of them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val version = Feelings.version
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()

    var days by remember { mutableStateOf<Map<String, HistoryDb.Weather>>(emptyMap()) }
    var first by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf<Zoom>(Zoom.All) }
    var picked by remember { mutableStateOf(Days.of(System.currentTimeMillis())) }
    var hour by remember { mutableStateOf<Int?>(null) }
    var hours by remember { mutableStateOf<Map<Int, Long>>(emptyMap()) }
    var felt by remember { mutableStateOf<List<HistoryDb.Mood>>(emptyList()) }
    var top by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(version) {
        withContext(Dispatchers.IO) {
            runCatching {
                val all = HistoryDb.get(context).weather("0")
                first = all.keys.minOrNull()
                days = all
            }
        }
    }
    LaunchedEffect(picked, version) {
        withContext(Dispatchers.IO) {
            runCatching {
                val db = HistoryDb.get(context)
                top = db.topOfDay(picked)?.label
                hours = db.hoursOf(picked)
                felt = db.moodsOn(picked)
            }
        }
    }

    val today = Days.of(System.currentTimeMillis())
    val order = remember(first, today) { first?.let { between(it, today) } ?: listOf(today) }
    val level = remember(order.size) { levelFor(order.size) }

    // the division plays when the card is actually on screen, not when the page first lays it out
    // somewhere below the fold, and plays again each time it comes back
    var inView by remember { mutableStateOf(false) }
    val grown = remember { Animatable(0f) }
    LaunchedEffect(inView, level) {
        if (inView) grown.animateTo(level.toFloat(), tween(600 + 280 * level, easing = FastOutSlowInEasing))
        else grown.snapTo(0f)
    }

    val ink = glass.t1
    val empty = glass.t1.copy(alpha = 0.06f)
    val future = glass.t1.copy(alpha = 0.025f)
    val peak = days.values.maxOfOrNull { it.listenedMs }?.coerceAtLeast(1L) ?: 1L
    fun tint(day: String): Color {
        if (day > today) return future
        val weather = days[day]
        return Moods.of(weather?.mood)?.let { Color(it.argb) }
            ?: weather?.listenedMs?.takeIf { it > 0 }?.let { ink.copy(alpha = 0.12f + 0.5f * (it.toFloat() / peak)) }
            ?: empty
    }

    val boxes: List<Box> = when (val at = zoom) {
        Zoom.All -> layoutAll(order, level, ::tint)
        is Zoom.Month -> layoutMonth(at.first, ::tint)
        is Zoom.Week -> layoutWeek(at.monday, today, days, ::tint)
        is Zoom.Day -> layoutDay(hours, felt, ink, empty)
    }

    // a change of zoom moves every box that exists on both sides, grows the new ones out of the
    // box that was tapped, and lets the rest fade
    var before by remember { mutableStateOf<List<Box>>(emptyList()) }
    var origin by remember { mutableStateOf<Rect?>(null) }
    val travel = remember { Animatable(1f) }
    fun go(next: Zoom, from: String? = null) {
        if (next == zoom) return
        before = boxes
        origin = from?.let { key -> boxes.firstOrNull { it.key == key }?.rect }
        zoom = next
        hour = null
        scope.launch {
            travel.snapTo(0f)
            travel.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        }
    }
    fun deeper(box: Box) {
        val day = box.day
        when (zoom) {
            Zoom.All -> day?.let { picked = it; go(Zoom.Month(monthOf(it)), box.key) }
            is Zoom.Month -> day?.let { picked = it; go(Zoom.Week(mondayOf(it)), box.key) }
            is Zoom.Week -> day?.takeIf { it <= today }?.let { picked = it; go(Zoom.Day(it), box.key) }
            is Zoom.Day -> box.hour?.let { hour = it }
        }
    }
    fun outward() {
        when (val at = zoom) {
            Zoom.All -> Unit
            is Zoom.Month -> go(Zoom.All)
            is Zoom.Week -> go(Zoom.Month(monthOf(at.monday)))
            is Zoom.Day -> go(Zoom.Week(mondayOf(at.day)))
        }
    }

    Spacer(Modifier.height(Space.small))
    GlassCard(padding = Space.medium) {
        Text("the weather", style = Type.section, color = glass.t1)
        Text(
            "every day the diary has. tap a square to see it, tap twice to open its month, week or day; pinch to come back out.",
            style = Type.note,
            color = glass.t3,
            modifier = Modifier.padding(top = 2.dp, bottom = Space.small),
        )

        // the trail back out, one step per level
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val trail = trailOf(zoom)
            trail.forEachIndexed { index, (label, target) ->
                if (index > 0) Text("  ›  ", style = Type.stamp, color = glass.t3)
                val last = index == trail.lastIndex
                Text(
                    label,
                    style = if (last) Type.metaStrong else Type.stamp,
                    color = if (last) glass.t1 else glass.accent,
                    maxLines = 1,
                    modifier = if (last) Modifier else Modifier.clickable { go(target) },
                )
            }
        }

        Text(
            reading(zoom, picked, today, days[picked], top, hour, hours, felt),
            style = Type.stamp,
            color = glass.t2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val labelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = glass.t1)
        val subStyle = TextStyle(fontSize = 10.sp, color = glass.t2)
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clipToBounds()
                .onGloballyPositioned { coordinates ->
                    val shown = coordinates.boundsInWindow().height
                    val whole = coordinates.size.height.toFloat()
                    if (whole > 0f) {
                        if (shown / whole > 0.5f) inView = true else if (shown <= 0f) inView = false
                    }
                }
                .pointerInput(zoom, boxes) {
                    // one tap asks what a day was, two go into it. the stock double tap detector
                    // holds every single tap back until it is sure no second is coming, which
                    // is a third of a second of lag on the common case. selecting is harmless, so
                    // it happens the moment the finger lifts, and a second tap then opens
                    fun under(at: Offset) = boxes.lastOrNull {
                        it.rect.contains(Offset(at.x / size.width, at.y / size.height))
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        under(up.position)?.let { box ->
                            box.day?.takeIf { it <= today }?.let { picked = it }
                            box.hour?.let { hour = it }
                        }
                        val again = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                            awaitFirstDown(requireUnconsumed = false)
                        } ?: return@awaitEachGesture
                        val near = (again.position - down.position).getDistance() < viewConfiguration.touchSlop * 4
                        if (near && waitForUpOrCancellation() != null) under(again.position)?.let { deeper(it) }
                    }
                }
                .pointerInput(zoom, boxes) {
                    // two fingers only, so a one finger drag still scrolls the page
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var start = 0f
                        var done = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val down = event.changes.filter { it.pressed }
                            if (down.isEmpty()) break
                            if (down.size < 2 || done) continue
                            val a = down[0].position
                            val b = down[1].position
                            val span = hypot(a.x - b.x, a.y - b.y)
                            if (start == 0f) start = span
                            val ratio = span / start
                            if (ratio > 1.35f) {
                                val middle = Offset((a.x + b.x) / 2 / size.width, (a.y + b.y) / 2 / size.height)
                                boxes.lastOrNull { it.rect.contains(middle) }?.let { deeper(it) }
                                done = true
                            } else if (ratio < 0.74f) {
                                outward()
                                done = true
                            }
                            event.changes.forEach { it.consume() }
                        }
                    }
                },
        ) {
            val side = size.width
            val p = travel.value
            // the growth only applies to the square of every day
            val scale = if (zoom == Zoom.All) Math.pow(2.0, (level - grown.value).toDouble()).toFloat() else 1f
            val now = boxes.associateBy { it.key }
            val was = before.associateBy { it.key }

            fun draw(box: Box, rect: Rect, alpha: Float, color: Color, labelled: Boolean) {
                val px = Rect(rect.left * side, rect.top * side, rect.right * side, rect.bottom * side)
                if (px.left >= side || px.top >= side || alpha <= 0.01f) return
                val gap = (minOf(px.width, px.height) * 0.1f).coerceAtMost(3.dp.toPx())
                val topLeft = Offset(px.left + gap / 2, px.top + gap / 2)
                val body = Size((px.width - gap).coerceAtLeast(0f), (px.height - gap).coerceAtLeast(0f))
                val radius = CornerRadius(minOf(body.width, body.height) * 0.2f)
                drawRoundRect(color, topLeft, body, radius, alpha = alpha)
                val chosen = (box.day != null && box.day == picked && zoom !is Zoom.Day) || (box.hour != null && box.hour == hour)
                if (chosen) drawRoundRect(ink, topLeft, body, radius, style = Stroke(width = gap.coerceAtLeast(1.5f)), alpha = alpha)
                if (!labelled || box.label == null || body.width < 26.dp.toPx()) return
                val text = measurer.measure(box.label, labelStyle)
                drawText(text, topLeft = Offset(topLeft.x + 5.dp.toPx(), topLeft.y + 4.dp.toPx()), alpha = alpha)
                box.sub?.takeIf { body.height > 60.dp.toPx() }?.let { sub ->
                    val line = measurer.measure(sub, subStyle)
                    drawText(line, topLeft = Offset(topLeft.x + 5.dp.toPx(), topLeft.y + body.height - line.size.height - 5.dp.toPx()), alpha = alpha)
                }
            }

            fun scaled(rect: Rect) = if (scale == 1f) rect else
                Rect(rect.left * scale, rect.top * scale, rect.right * scale, rect.bottom * scale)

            for (box in before) {
                if (box.key !in now && p < 1f) draw(box, box.rect, 1f - p, box.color, labelled = true)
            }
            for (box in boxes) {
                val target = scaled(box.rect)
                val old = was[box.key]
                when {
                    p >= 1f -> draw(box, target, 1f, box.color, labelled = true)
                    old != null -> draw(box, lerpRect(old.rect, target, p), 1f, lerp(old.color, box.color, p), labelled = p > 0.7f)
                    else -> draw(box, lerpRect(origin ?: target, target, p), p, box.color, labelled = p > 0.7f)
                }
            }
        }

        val shown = Moods.all.filter { mood -> days.values.any { it.mood == mood.name } }
        if (shown.isNotEmpty()) {
            FlowRow(
                Modifier.padding(top = Space.small),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (mood in shown) {
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

private fun lerpRect(a: Rect, b: Rect, t: Float) = Rect(
    a.left + (b.left - a.left) * t,
    a.top + (b.top - a.top) * t,
    a.right + (b.right - a.right) * t,
    a.bottom + (b.bottom - a.bottom) * t,
)

// the four layouts, each in a unit square

private fun layoutAll(order: List<String>, level: Int, tint: (String) -> Color): List<Box> {
    val cell = 1f / (1 shl level)
    return order.mapIndexed { index, day ->
        val x = spread(index) * cell
        val y = spread(index shr 1) * cell
        Box("d:$day", Rect(x, y, x + cell, y + cell), tint(day), day = day)
    }
}

private fun layoutMonth(first: String, tint: (String) -> Color): List<Box> {
    val calendar = calendarOf(first)
    val length = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val offset = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    val rows = (offset + length + 6) / 7
    val cell = 1f / 7
    val top = (1f - rows * cell) / 2
    return (0 until length).map { index ->
        val day = Days.of(calendar.timeInMillis)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val slot = offset + index
        val x = (slot % 7) * cell
        val y = top + (slot / 7) * cell
        Box("d:$day", Rect(x, y, x + cell, y + cell), tint(day), label = (index + 1).toString(), day = day)
    }
}

private fun layoutWeek(
    monday: String,
    today: String,
    days: Map<String, HistoryDb.Weather>,
    tint: (String) -> Color,
): List<Box> {
    val calendar = calendarOf(monday)
    val name = SimpleDateFormat("EEE d", Locale.getDefault())
    val cell = 1f / 7
    return (0 until 7).map { index ->
        val day = Days.of(calendar.timeInMillis)
        val label = name.format(calendar.time).lowercase()
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val listened = days[day]?.listenedMs?.takeIf { it > 0 && day <= today }
        Box(
            "d:$day",
            Rect(index * cell, 0f, (index + 1) * cell, 1f),
            tint(day),
            label = label,
            sub = listened?.let { Stats.minutes(it) } ?: days[day]?.mood,
            day = day,
        )
    }
}

private fun layoutDay(hours: Map<Int, Long>, felt: List<HistoryDb.Mood>, ink: Color, empty: Color): List<Box> {
    val peak = hours.values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val moodByHour = felt.associateBy { Days.hourOf(it.at) }
    val cell = 1f / 6
    val top = (1f - 4 * cell) / 2
    return (0 until 24).map { h ->
        val x = (h % 6) * cell
        val y = top + (h / 6) * cell
        val color = Moods.of(moodByHour[h]?.mood)?.let { Color(it.argb) }
            ?: hours[h]?.takeIf { it > 0 }?.let { ink.copy(alpha = 0.12f + 0.5f * (it.toFloat() / peak)) }
            ?: empty
        Box("h:$h", Rect(x, y, x + cell, y + cell), color, label = "%02d".format(h), hour = h)
    }
}

private fun reading(
    zoom: Zoom,
    picked: String,
    today: String,
    weather: HistoryDb.Weather?,
    top: String?,
    hour: Int?,
    hours: Map<Int, Long>,
    felt: List<HistoryDb.Mood>,
): String {
    if (zoom is Zoom.Day && hour != null) {
        val mood = felt.lastOrNull { Days.hourOf(it.at) == hour }
        return listOfNotNull(
            pretty(picked) + ", %02d:00".format(hour),
            hours[hour]?.takeIf { it > 0 }?.let { Stats.minutes(it) } ?: "nothing played",
            mood?.let { m -> m.mood + (m.title?.let { " with $it" } ?: "") },
        ).joinToString(" · ")
    }
    return listOfNotNull(
        pretty(picked) + if (picked == today) ", so far" else "",
        weather?.listenedMs?.takeIf { it > 0 }?.let { Stats.minutes(it) } ?: "nothing played",
        weather?.mood?.let { mood -> mood + if (weather.moods > 1) " (${weather.moods} noted)" else "" },
        top?.let { "most: $it" },
    ).joinToString(" · ")
}

private fun trailOf(zoom: Zoom): List<Pair<String, Zoom>> {
    val month = SimpleDateFormat("MMMM", Locale.getDefault())
    val week = SimpleDateFormat("d MMM", Locale.getDefault())
    val out = mutableListOf<Pair<String, Zoom>>("all days" to Zoom.All)
    val day = when (zoom) {
        Zoom.All -> return out
        is Zoom.Month -> zoom.first
        is Zoom.Week -> zoom.monday
        is Zoom.Day -> zoom.day
    }
    out += month.format(calendarOf(day).time).lowercase() to Zoom.Month(monthOf(day))
    if (zoom is Zoom.Month) return out
    out += "week of " + week.format(calendarOf(mondayOf(day)).time).lowercase() to Zoom.Week(mondayOf(day))
    if (zoom is Zoom.Week) return out
    out += pretty(day) to zoom
    return out
}

private fun calendarOf(day: String): Calendar = Calendar.getInstance().apply {
    runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)!! }.getOrNull()?.let { time = it }
    set(Calendar.HOUR_OF_DAY, 12)
}

private fun monthOf(day: String): String = day.take(7) + "-01"

private fun mondayOf(day: String): String {
    val calendar = calendarOf(day)
    val back = (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    calendar.add(Calendar.DAY_OF_YEAR, -back)
    return Days.of(calendar.timeInMillis)
}

private fun pretty(day: String): String = runCatching {
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(calendarOf(day).time).lowercase()
}.getOrDefault(day)

/** Divisions needed to hold this many days: one square holds one, then four, sixteen, sixty four. */
private fun levelFor(count: Int): Int {
    var level = 0
    while ((1L shl (2 * level)) < count) level++
    return level
}

/** A day's place in the Z order, split back into its column (even bits) or row (odd bits). */
private fun spread(index: Int): Int {
    var out = 0
    var bit = 0
    var rest = index
    while (rest != 0) {
        if (rest and 1 != 0) out = out or (1 shl bit)
        rest = rest shr 2
        bit++
    }
    return out
}

/** Every day from the first to the last, both included. */
private fun between(first: String, last: String): List<String> {
    val calendar = calendarOf(first)
    val out = mutableListOf<String>()
    while (out.size < 20_000) {
        val day = Days.of(calendar.timeInMillis)
        if (day > last) break
        out.add(day)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    return out.ifEmpty { listOf(last) }
}
