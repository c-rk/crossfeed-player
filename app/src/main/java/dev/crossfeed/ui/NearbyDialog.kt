package dev.crossfeed.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.net.Neighbour
import dev.crossfeed.core.net.Presence
import dev.crossfeed.core.net.Social
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun NearbyDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val broadcasting = remember { Prefs(context).broadcast }

    var people by remember { mutableStateOf(emptyList<Neighbour>()) }
    var selected by remember { mutableStateOf<Neighbour?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var sweeps by remember { mutableIntStateOf(0) }
    var located by remember { mutableStateOf<Boolean?>(null) }
    var scanning by remember { mutableStateOf(true) }

    LaunchedEffect(sweeps) {
        scanning = true
        note = null
        if (!Presence.allowed(context)) {
            located = false
            note = "crossfeed needs location access to find people near you"
            scanning = false
            return@LaunchedEffect
        }
        val fix = Presence.locate(context)
        if (fix == null) {
            located = false
            note = "cannot get a location fix — is location turned on?"
            scanning = false
            return@LaunchedEffect
        }
        Presence.push(context)
        runCatching { people = Social.nearby(context) }
            .onSuccess { located = true }
            .onFailure { note = it.message }
        scanning = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(25_000)
            if (located == true) {
                runCatching { people = Social.nearby(context) }
            }
        }
    }

    val turn = rememberInfiniteTransition(label = "radar")
    val angle by turn.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(strong = true) {
            SectionHeader("nearby") {
                Text("${people.size} found", style = Type.footnote, color = glass.inkFaint)
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                    val radius = size.minDimension / 2
                    val centre = Offset(size.width / 2, size.height / 2)
                    for (ring in 1..3) {
                        drawCircle(
                            color = glass.stroke,
                            radius = radius * ring / 3f,
                            center = centre,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f),
                        )
                    }
                    val radians = Math.toRadians(angle.toDouble())
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                glass.accent.copy(alpha = 0f),
                                glass.accent.copy(alpha = 0.45f),
                            ),
                            centre,
                        ),
                        startAngle = angle - 60f,
                        sweepAngle = 60f,
                        useCenter = true,
                        topLeft = Offset(centre.x - radius, centre.y - radius),
                        size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    )
                    drawLine(
                        color = glass.accent,
                        start = centre,
                        end = Offset(
                            centre.x + (radius * cos(radians)).toFloat(),
                            centre.y + (radius * sin(radians)).toFloat(),
                        ),
                        strokeWidth = 2f,
                    )
                }

                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(glass.ink.copy(alpha = 0.8f)),
                )

                for (person in people) {
                    val slot = (person.handle.hashCode() and 0x7fffffff) % 360
                    val distance = (person.metres.coerceIn(20, 5000) / 5000f).coerceIn(0.16f, 0.94f)
                    val radians = Math.toRadians(slot.toDouble())
                    Box(
                        Modifier
                            .offset(
                                x = (cos(radians) * distance * 130).dp,
                                y = (sin(radians) * distance * 130).dp,
                            )
                            .size(if (selected?.id == person.id) 22.dp else 16.dp)
                            .clip(CircleShape)
                            .background(
                                if (person.link == "accepted") glass.positive else glass.accent,
                            )
                            .clickable { selected = if (selected?.id == person.id) null else person },
                    )
                }
            }

            if (!broadcasting) {
                Text(
                    "your own broadcast is off. you can see people, they cannot see you.",
                    style = Type.footnote,
                    color = glass.inkMuted,
                    modifier = Modifier.padding(top = Space.small),
                )
            }

            val person = selected
            if (person != null) {
                Spacer(Modifier.height(Space.small))
                Text("@${person.handle}", style = Type.title, color = glass.ink)
                Text(
                    listOfNotNull(
                        distanceLabel(person.metres),
                        person.title?.let { "playing $it" },
                    ).joinToString(" · "),
                    style = Type.footnote,
                    color = glass.inkMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.small))
                GlassButton(
                    label = when (person.link) {
                        "accepted" -> "already connected"
                        "pending" -> "request sent"
                        else -> "send a connection request"
                    },
                    filled = person.link == "none",
                    enabled = person.link == "none",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            runCatching { Social.request(context, id = person.id) }
                                .onSuccess { note = "request sent to @${person.handle}" }
                                .onFailure { note = it.message }
                            sweeps++
                            onChanged()
                        }
                    },
                )
            } else if (people.isEmpty()) {
                Text(
                    when {
                        scanning -> "scanning…"
                        located == false -> "not scanning yet"
                        else -> "no one broadcasting within 5 km right now."
                    },
                    style = Type.body,
                    color = glass.inkMuted,
                    modifier = Modifier.padding(vertical = Space.small),
                )
            }

            note?.let {
                Spacer(Modifier.height(Space.tight))
                Text(it, style = Type.footnote, color = glass.inkMuted)
            }

            Spacer(Modifier.height(Space.small))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                GlassButton(label = "scan again", modifier = Modifier.weight(1f), onClick = { sweeps++ })
                GlassButton(label = "done", modifier = Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

private fun distanceLabel(metres: Int) = when {
    metres <= 200 -> "within 200 m"
    metres <= 1000 -> "within 1 km"
    metres <= 2500 -> "within 2.5 km"
    else -> "within 5 km"
}
