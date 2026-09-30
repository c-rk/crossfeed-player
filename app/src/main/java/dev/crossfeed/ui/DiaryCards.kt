package dev.crossfeed.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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

/**
 * Every day the diary has, as one square that keeps dividing.
 *
 * The first day is the whole square. Days two to four split it in four, and each time it outgrows
 * itself the whole thing shrinks into its top left quarter and the new days fill the other three.
 * Days are laid in that same order, a Z within a Z, which is what lets the old square survive
 * intact inside the new one rather than reflowing. Each time the card appears it grows from the
 * single square to where the diary is now, so the shrinking is something you see.
 *
 * A day with a mood noted wears that mood; a day without one is grey, deeper the longer you
 * listened. Tapping a square says what that day was.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val version = Feelings.version

    var days by remember { mutableStateOf<Map<String, HistoryDb.Weather>>(emptyMap()) }
    var first by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf(Days.of(System.currentTimeMillis())) }
    var top by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(version) {
        withContext(Dispatchers.IO) {
            runCatching {
                val db = HistoryDb.get(context)
                val all = db.weather("0")
                // the square starts on the first day anything was played or felt
                first = all.keys.minOrNull()
                days = all
            }
        }
    }
    LaunchedEffect(picked, version) {
        top = withContext(Dispatchers.IO) { runCatching { HistoryDb.get(context).topOfDay(picked) }.getOrNull()?.label }
    }

    val today = Days.of(System.currentTimeMillis())
    val order = remember(first, today) { first?.let { between(it, today) } ?: listOf(today) }
    // how many times the square has divided: enough quarters, of quarters, to hold every day
    val level = remember(order.size) { levelFor(order.size) }
    val grown = remember { Animatable(0f) }
    LaunchedEffect(level) {
        grown.animateTo(level.toFloat(), tween(durationMillis = 500 + 260 * level, easing = FastOutSlowInEasing))
    }

    val peak = days.values.maxOfOrNull { it.listenedMs }?.coerceAtLeast(1L) ?: 1L
    val felt = Moods.all.filter { mood -> days.values.any { it.mood == mood.name } }

    Spacer(Modifier.height(Space.small))
    GlassCard(padding = Space.medium) {
        Text("the weather", style = Type.section, color = glass.t1)
        Text(
            "every day the diary has, one square that keeps dividing. a noted mood colours its day.",
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
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val empty = glass.t1.copy(alpha = 0.06f)
        val ink = glass.t1
        Canvas(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clipToBounds()
                .pointerInput(order, level) {
                    detectTapGestures { at ->
                        // taps are read against the square as it finally stands
                        val cell = size.width.toFloat() / (1 shl level)
                        val x = (at.x / cell).toInt()
                        val y = (at.y / cell).toInt()
                        val index = interleave(x, y)
                        order.getOrNull(index)?.let { picked = it }
                    }
                },
        ) {
            val side = size.width
            // laid out at the final division, then seen through a zoom that starts on the first
            // square alone and pulls back until the whole diary fits
            val zoom = Math.pow(2.0, (level - grown.value).toDouble()).toFloat()
            val cell = side / (1 shl level) * zoom
            val gap = (cell * 0.1f).coerceAtMost(3.dp.toPx())
            val radius = CornerRadius((cell - gap) * 0.2f)
            order.forEachIndexed { index, day ->
                val x = spread(index) * cell
                val y = spread(index shr 1) * cell
                if (x >= side || y >= side) return@forEachIndexed
                val weather = days[day]
                val color = moodColor(weather?.mood)
                    ?: weather?.listenedMs?.takeIf { it > 0 }
                        ?.let { ink.copy(alpha = 0.12f + 0.5f * (it.toFloat() / peak)) }
                    ?: empty
                val topLeft = Offset(x + gap / 2, y + gap / 2)
                val box = Size(cell - gap, cell - gap)
                drawRoundRect(color, topLeft, box, radius)
                if (day == picked) drawRoundRect(ink, topLeft, box, radius, style = Stroke(width = gap.coerceAtLeast(1.5f)))
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

/** The other way round: a column and a row back to the day's place in the order. */
private fun interleave(x: Int, y: Int): Int {
    var out = 0
    for (bit in 0 until 16) {
        out = out or (((x shr bit) and 1) shl (2 * bit)) or (((y shr bit) and 1) shl (2 * bit + 1))
    }
    return out
}

/** Every day from the first to the last, both included. */
private fun between(first: String, last: String): List<String> {
    val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val start = runCatching { format.parse(first)!! }.getOrNull() ?: return listOf(last)
    val calendar = Calendar.getInstance().apply { time = start; set(Calendar.HOUR_OF_DAY, 12) }
    val out = mutableListOf<String>()
    while (out.size < 20_000) {
        val day = Days.of(calendar.timeInMillis)
        if (day > last) break
        out.add(day)
        calendar.add(Calendar.DAY_OF_YEAR, 1)
    }
    return out.ifEmpty { listOf(last) }
}

private fun pretty(day: String): String = runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(day)!!
    SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(date).lowercase()
}.getOrDefault(day)

private fun clock(at: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
