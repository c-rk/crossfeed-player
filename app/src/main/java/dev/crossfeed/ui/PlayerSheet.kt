package dev.crossfeed.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.crossfeed.core.player.PlayerEngine
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val MINI_HEIGHT = 64.dp
private val QUEUE_ROW = 58.dp

@Composable
fun PlayerSheet(navBar: @Composable () -> Unit) {
    val glass = LocalGlass.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val deck = rememberDeck()

    var expanded by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    BackHandler(enabled = expanded) {
        if (showQueue) showQueue = false else expanded = false
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullPx = with(density) { maxHeight.toPx() }
        val offset = remember { Animatable(fullPx) }

        LaunchedEffect(expanded) {
            offset.animateTo(
                if (expanded) 0f else fullPx,
                spring(dampingRatio = 0.9f, stiffness = 340f),
            )
            if (!expanded) showQueue = false
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(glass.deep),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!expanded && deck != null) {
                Box(
                    Modifier
                        .padding(horizontal = Space.small, vertical = Space.tight)
                        .clip(RoundedCornerShape(18.dp))
                        .background(glass.sheet)
                        .pointerInput(Unit) {
                            var travel = 0f
                            detectVerticalDragGestures(
                                onDragStart = { travel = 0f },
                                onVerticalDrag = { change, delta ->
                                    travel += delta
                                    change.consume()
                                },
                                onDragEnd = { if (travel < -40f) expanded = true },
                            )
                        },
                ) {
                    MiniBar(deck = deck, onOpen = { expanded = true })
                }
            }
            navBar()
            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }

        if (offset.value < fullPx && deck != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = offset.value }
                    .background(glass.deep)
                    .pointerInput(showQueue) {
                        var travel = 0f
                        detectVerticalDragGestures(
                            onDragStart = { travel = 0f },
                            onVerticalDrag = { change, delta ->
                                travel += delta
                                change.consume()
                                if (!showQueue && travel > 0f) {
                                    scope.launch { offset.snapTo(travel.coerceIn(0f, fullPx)) }
                                }
                            },
                            onDragEnd = {
                                when {
                                    showQueue && travel > 60f -> showQueue = false
                                    !showQueue && travel < -60f && deck.local -> showQueue = true
                                    !showQueue && travel > 110f -> expanded = false
                                    else -> scope.launch {
                                        offset.animateTo(0f, spring(dampingRatio = 0.9f, stiffness = 340f))
                                    }
                                }
                            },
                        )
                    },
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    Handle(onTap = { expanded = false })
                    Crossfade(
                        targetState = showQueue,
                        animationSpec = tween(180),
                        label = "pane",
                        modifier = Modifier.weight(1f),
                    ) { queue ->
                        if (queue) QueuePane() else NowPlayingPane(deck)
                    }
                    Text(
                        when {
                            showQueue -> "swipe down for the player"
                            deck.local -> "swipe up for the queue"
                            else -> "its queue stays in that app"
                        },
                        style = Type.caps,
                        color = glass.inkFaint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = Space.medium),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun Handle(onTap: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .pointerInput(Unit) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 38.dp, height = 4.dp)
                .clip(CircleShape)
                .background(glass.inkFaint.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun MiniBar(deck: Deck, onOpen: () -> Unit) {
    val glass = LocalGlass.current
    val progress = if (deck.durationMs > 0) {
        (deck.positionMs.toFloat() / deck.durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(2.dp).background(glass.strokeSoft)) {
            Box(Modifier.fillMaxWidth(progress).height(2.dp).background(glass.accent))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(MINI_HEIGHT)
                .pointerInput(Unit) { detectTapGestures { onOpen() } }
                .padding(horizontal = Space.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackArt(deck.artwork, deck.title, 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = Space.small)) {
                Text(
                    deck.title,
                    style = Type.callout,
                    color = glass.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    deck.artist ?: "unknown artist",
                    style = Type.footnote,
                    color = glass.inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (deck.controllable) {
                IconAction(
                    glyph = if (deck.playing) Glyph.PAUSE else Glyph.PLAY,
                    filled = true,
                    diameter = 40.dp,
                ) { deck.toggle() }
                Spacer(Modifier.size(Space.tight))
                IconAction(glyph = Glyph.NEXT, diameter = 34.dp) { deck.next() }
            }
        }
    }
}

@Composable
private fun NowPlayingPane(deck: Deck) {
    val glass = LocalGlass.current
    val context = LocalContext.current

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = Space.large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.82f)
                .pointerInput(deck.id) {
                    detectHorizontalSwipe(
                        onLeft = { deck.next() },
                        onRight = { deck.previous() },
                    )
                },
        ) {
            TrackArt(deck.artwork, deck.title, null)
        }

        Spacer(Modifier.height(Space.large))
        Text(
            deck.title,
            style = Type.title,
            color = glass.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            deck.artist ?: "unknown artist",
            style = Type.body,
            color = glass.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!deck.local) {
            Text(
                "playing in " + appLabel(context, deck.source.orEmpty()),
                style = Type.caps,
                color = glass.accent,
                modifier = Modifier.padding(top = Space.tight),
            )
        }

        Spacer(Modifier.height(Space.large))
        Scrubber(deck.positionMs, deck.durationMs) { deck.seekTo(it) }

        Spacer(Modifier.height(Space.large))
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(glyph = Glyph.PREVIOUS, diameter = 48.dp) { deck.previous() }
            IconAction(
                glyph = if (deck.playing) Glyph.PAUSE else Glyph.PLAY,
                filled = true,
                diameter = 74.dp,
            ) { deck.toggle() }
            IconAction(glyph = Glyph.NEXT, diameter = 48.dp) { deck.next() }
        }
    }
}

@Composable
private fun QueuePane() {
    val glass = LocalGlass.current
    val density = LocalDensity.current
    val state by PlayerEngine.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val rowPx = with(density) { QUEUE_ROW.toPx() }

    var dragIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.large)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("up next", style = Type.wordmark, color = glass.ink, modifier = Modifier.weight(1f))
            Text("${state.queue.size}", style = Type.body, color = glass.inkFaint)
        }
        Text(
            "hold a row to drag it",
            style = Type.footnote,
            color = glass.inkFaint,
            modifier = Modifier.padding(bottom = Space.small),
        )

        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
            items(state.queue, key = { it.id }) { track ->
                val index = state.queue.indexOfFirst { it.id == track.id }
                val dragging = index == dragIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(QUEUE_ROW)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            if (dragging) {
                                translationY = dragOffset
                                scaleX = 1.02f
                                scaleY = 1.02f
                            }
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (dragging) glass.fill else Color.Transparent)
                        .pointerInput(track.id) {
                            detectTapGestures { PlayerEngine.skipTo(index) }
                        }
                        .pointerInput(track.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragIndex = index
                                    dragOffset = 0f
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val steps = (dragOffset / rowPx).roundToInt()
                                    if (steps != 0) {
                                        val from = dragIndex
                                        val to = (from + steps).coerceIn(0, state.queue.lastIndex)
                                        if (to != from) {
                                            PlayerEngine.move(from, to)
                                            dragIndex = to
                                            dragOffset -= steps * rowPx
                                        }
                                    }
                                },
                                onDragEnd = {
                                    dragIndex = -1
                                    dragOffset = 0f
                                },
                                onDragCancel = {
                                    dragIndex = -1
                                    dragOffset = 0f
                                },
                            )
                        }
                        .padding(horizontal = Space.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        style = Type.footnote,
                        color = if (index == state.index) glass.accent else glass.inkFaint,
                        modifier = Modifier.size(width = 26.dp, height = 18.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            track.title,
                            style = Type.callout,
                            color = if (index == state.index) glass.accent else glass.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            track.artist ?: "unknown artist",
                            style = Type.footnote,
                            color = glass.inkFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(glass.fill)
                            .pointerInput(track.id) {
                                detectTapGestures { PlayerEngine.remove(index) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = Type.headline, color = glass.inkMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Scrubber(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    val glass = LocalGlass.current
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0) {
                            onSeek((durationMs * (offset.x / size.width).coerceIn(0f, 1f)).toLong())
                        }
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(glass.fill))
            Box(Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(glass.accent))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(positionMs), style = Type.footnote, color = glass.inkFaint)
            Text(clock(durationMs), style = Type.footnote, color = glass.inkFaint)
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectHorizontalSwipe(
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    var travel = 0f
    detectHorizontalDragGestures(
        onDragStart = { travel = 0f },
        onHorizontalDrag = { change, delta ->
            travel += delta
            change.consume()
        },
        onDragEnd = {
            if (abs(travel) > 70f) {
                if (travel < 0) onLeft() else onRight()
            }
        },
    )
}

private fun clock(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
}
