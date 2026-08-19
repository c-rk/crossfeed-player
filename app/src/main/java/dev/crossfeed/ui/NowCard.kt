package dev.crossfeed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.crossfeed.core.Permissions
import dev.crossfeed.core.history.Command
import dev.crossfeed.core.history.ListeningService
import dev.crossfeed.core.history.NowPlaying
import dev.crossfeed.core.history.Sessions
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.delay

/**
 * Whatever is playing, wherever it is playing, at the top of the listening page.
 *
 * The controls are the operating system's own, so a song in spotify or apple music answers the
 * same buttons a local file does. Nothing here asks the other app for permission.
 */
@Composable
fun NowCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val now = NowPlaying.current
    val live = remember(now?.source, Sessions.available) { Sessions.controller(now?.source) != null }

    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }
    var held by remember { mutableFloatStateOf(0f) }

    // the reported position is a moment plus a rate, so it only looks live if it is re-read
    LaunchedEffect(now?.key, now?.playing) {
        while (now != null) {
            if (!dragging) position = now.positionNow()
            delay(500)
        }
    }

    if (now == null) {
        if (ListeningService.enabled(context)) return
        GlassCard {
            SectionHeader("nothing playing")
            Text(
                "with live control on, whatever plays in any app shows up here and can be paused, " +
                    "skipped and scrubbed without leaving crossfeed.",
                style = Type.footnote,
                color = glass.inkMuted,
                modifier = Modifier.padding(bottom = Space.small),
            )
            GlassButton(
                label = "turn on live control",
                filled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { Permissions.openListenerSettings(context) },
            )
        }
        return
    }

    val duration = now.durationMs
    val app = remember(now.source) { appLabel(context, now.source) }

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(Shapes.tile)) {
                TrackArt(now.artwork, now.title, 62.dp)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = Space.small),
            ) {
                Text(
                    now.title,
                    style = Type.headline,
                    color = glass.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    now.artist ?: "unknown artist",
                    style = Type.footnote,
                    color = glass.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (now.playing) "on " + app else "on " + app + " · paused",
                    style = Type.caps,
                    color = if (now.playing) glass.accent else glass.inkFaint,
                    maxLines = 1,
                )
            }
        }

        if (duration > 0) {
            Slider(
                value = if (dragging) held else (position.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = {
                    dragging = true
                    held = it
                },
                onValueChangeFinished = {
                    val target = (held * duration).toLong()
                    position = target
                    dragging = false
                    if (live) Sessions.seek(target, now.source)
                },
                enabled = live,
                colors = SliderDefaults.colors(
                    thumbColor = glass.accent,
                    activeTrackColor = glass.accent,
                    inactiveTrackColor = glass.fill,
                    disabledThumbColor = glass.inkFaint,
                    disabledActiveTrackColor = glass.inkFaint,
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(clock(position), style = Type.caps, color = glass.inkFaint)
                Spacer(Modifier.weight(1f))
                Text(clock(duration), style = Type.caps, color = glass.inkFaint)
            }
        }

        Spacer(Modifier.height(Space.small))

        if (live) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.small, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(glyph = Glyph.PREVIOUS, diameter = 40.dp) {
                    Sessions.transport(Command.PREVIOUS, now.source)
                }
                IconAction(
                    glyph = if (now.playing) Glyph.PAUSE else Glyph.PLAY,
                    filled = true,
                    diameter = 52.dp,
                ) {
                    Sessions.transport(
                        if (now.playing) Command.PAUSE else Command.PLAY,
                        now.source,
                    )
                }
                IconAction(glyph = Glyph.NEXT, diameter = 40.dp) {
                    Sessions.transport(Command.NEXT, now.source)
                }
            }
        } else {
            Text(
                app + " is not answering right now, so this is a reading only. it usually comes " +
                    "back on its own when the song changes.",
                style = Type.footnote,
                color = glass.inkFaint,
            )
        }
    }
}

private fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return minutes.toString() + ":" + seconds.toString().padStart(2, '0')
}
