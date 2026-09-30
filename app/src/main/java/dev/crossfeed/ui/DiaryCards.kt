package dev.crossfeed.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
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
 * Six colours on one line, arriving one after another and then breathing. A tap notes the mood
 * against whatever is playing, if anything is, says so for a moment, and folds the card away. It
 * comes back after a couple of minutes, because a check-in that is always there stops being
 * noticed. It never leaves the phone.
 */
@Composable
fun MoodCard(deck: Deck?) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf<HistoryDb.Mood?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(true) }
    var chosen by remember { mutableStateOf<HistoryDb.Mood?>(null) }

    LaunchedEffect(Unit) {
        last = withContext(Dispatchers.IO) { runCatching { HistoryDb.get(context).lastMood() }.getOrNull() }
        loaded = true
    }
    LaunchedEffect(last, loaded) {
        if (!loaded) return@LaunchedEffect
        val since = last?.let { System.currentTimeMillis() - it.at } ?: Long.MAX_VALUE
        if (since < QUIET_MS) {
            hidden = true
            delay(QUIET_MS - since)
        }
        chosen = null
        hidden = false
    }

    AnimatedVisibility(
        visible = !hidden,
        enter = expandVertically(tween(420)) + fadeIn(tween(420)),
        exit = shrinkVertically(tween(420)) + fadeOut(tween(300)),
    ) {
        Column {
            GlassCard(padding = Space.medium) {
                Text("how does it feel", style = Type.section, color = glass.t1)
                Spacer(Modifier.height(Space.small))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Moods.all.forEachIndexed { index, mood ->
                        MoodDot(mood, index, chosen?.mood) {
                            if (chosen != null) return@MoodDot
                            val playing = deck?.takeIf { it.playing }
                            scope.launch {
                                val noted = withContext(Dispatchers.IO) {
                                    HistoryDb.get(context).noteMood(mood.name, playing?.title, playing?.artist)
                                }
                                chosen = noted
                                Feelings.changed()
                                // long enough to read what was noted, then the card folds away
                                delay(1_400)
                                last = noted
                            }
                        }
                    }
                }
                Text(
                    chosen?.let { mood -> "noted " + mood.mood + (mood.title?.let { " with $it" } ?: "") }
                        ?: "a tap notes it against whatever is playing. it stays on this phone.",
                    style = Type.note,
                    color = if (chosen != null) glass.t1 else glass.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Space.small),
                )
            }
            Spacer(Modifier.height(Space.small))
        }
    }
}

private const val QUIET_MS = 2 * 60_000L

@Composable
private fun MoodDot(mood: Moods.Mood, index: Int, chosen: String?, onPick: () -> Unit) {
    val glass = LocalGlass.current
    val tint = Color(mood.argb)

    // one after another, left to right
    val arrive = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(90L * index)
        arrive.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 320f))
    }
    // and then each breathes a little out of step with its neighbours
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(1_600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(220 * index),
        ),
        label = "breath",
    )
    val picked = chosen == mood.name
    val focus by animateFloatAsState(
        when {
            chosen == null -> 1f
            picked -> 1.3f
            else -> 0.7f
        },
        spring(dampingRatio = 0.55f),
        label = "focus",
    )
    val fade by animateFloatAsState(if (chosen != null && !picked) 0.25f else 1f, tween(300), label = "fade")

    Column(
        Modifier
            .clip(Shapes.chip)
            .clickable(onClick = onPick)
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .graphicsLayer { alpha = fade },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(
            Modifier
                .size(34.dp)
                .graphicsLayer {
                    val scale = arrive.value * focus * (if (chosen == null) breath else 1f)
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(tint, tint.copy(alpha = 0.55f))))
                .border(1.dp, tint.copy(alpha = 0.9f), CircleShape),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            mood.name,
            style = Type.stamp,
            color = if (picked) glass.t1 else glass.t3,
            modifier = Modifier.graphicsLayer { alpha = arrive.value },
        )
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
        Text(ago(found.daysAgo), style = Type.section, color = glass.t1)
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

private fun clock(at: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
