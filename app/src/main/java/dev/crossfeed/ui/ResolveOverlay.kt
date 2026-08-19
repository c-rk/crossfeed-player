package dev.crossfeed.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.crossfeed.core.Cache
import dev.crossfeed.core.LibraryItem
import dev.crossfeed.core.Opener
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.RecentEntry
import dev.crossfeed.core.Resolved
import dev.crossfeed.core.Resolver
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ResolveOverlay(url: String, shared: Boolean = false, onDone: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val prefs = remember { Prefs(context) }
    val cache = remember { Cache(context) }

    var resolved by remember { mutableStateOf<Resolved?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        launch {
            delay(180)
            if (resolved == null) visible = true
        }
        val outcome = Resolver.resolve(context, url)
        outcome.meta?.let {
            cache.remember(
                RecentEntry(
                    sourceUrl = outcome.sourceUrl,
                    sourceLabel = outcome.source?.label ?: "link",
                    targetLabel = if (outcome.local != null) "local" else outcome.primary?.platform?.label ?: "none",
                    title = it.title,
                    artist = it.artist,
                    exact = outcome.decided,
                    at = System.currentTimeMillis(),
                ),
            )
        }
        if (prefs.autoOpen && outcome.decided && !shared) {
            openResolved(context, outcome)
            onDone()
        } else {
            resolved = outcome
            visible = true
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(if (visible) Color.Black.copy(alpha = if (glass.dark) 0.45f else 0.25f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(140)) + slideInVertically(tween(220)) { it / 3 },
            exit = fadeOut(tween(100)),
        ) {
            val shape = Shapes.sheet
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Brush.verticalGradient(glass.wash.map { it.copy(alpha = 0.94f) }))
                    .border(1.dp, Brush.verticalGradient(listOf(glass.stroke, Color.Transparent)), shape)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(Space.large),
            ) {
                Grabber()
                val outcome = resolved
                if (outcome == null) {
                    Text("resolving…", style = Type.title, color = glass.ink)
                    Text(url, style = Type.footnote, color = glass.inkMuted, maxLines = 1)
                    Spacer(Modifier.height(Space.medium))
                } else {
                    Sheet(outcome, onDone)
                }
            }
        }
    }
}

@Composable
private fun Grabber() {
    val glass = LocalGlass.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(38.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(glass.stroke),
        )
    }
    Spacer(Modifier.height(Space.medium))
}

@Composable
private fun Sheet(outcome: Resolved, onDone: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val meta = outcome.meta

    if (meta == null) {
        Text(outcome.error ?: "nothing found", style = Type.title, color = glass.warning)
        Text(
            outcome.sourceUrl,
            style = Type.footnote,
            color = glass.inkFaint,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp, bottom = Space.medium),
        )
        GlassButton(
            label = "open the original",
            filled = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                Opener.openWeb(context, outcome.sourceUrl)
                onDone()
            },
        )
        return
    }

    val item = LibraryItem(
        title = meta.title,
        artist = meta.artist,
        album = meta.album,
        durationMs = meta.durationMs,
        local = outcome.local,
        artwork = meta.artwork,
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        ArtworkTile(item, size = 60.dp)
        Column(Modifier.padding(start = Space.medium)) {
            Text(
                meta.title,
                style = Type.title,
                color = glass.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            meta.artist?.let {
                Text(it, style = Type.body, color = glass.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    Spacer(Modifier.height(Space.medium))
    Text(
        text = if (outcome.routes.size > 1) "open where?" else "from ${outcome.source?.label ?: "link"}",
        style = Type.caps,
        color = glass.inkMuted,
        modifier = Modifier.padding(bottom = Space.tight),
    )

    outcome.local?.let { local ->
        GlassButton(
            label = "play local · ${local.format}",
            filled = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                Opener.openLocal(context, local)
                onDone()
            },
        )
        Spacer(Modifier.height(Space.tight))
    }

    for (route in outcome.routes) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                PlatformRow(
                    platform = route.platform,
                    badge = if (route.exact) "exact" else "search",
                ) {
                    Opener.open(context, route.platform, route.url)
                    onDone()
                }
            }
            IconAction(glyph = Glyph.COPY, diameter = 32.dp) { copyLink(context, route.url) }
        }
    }

    Spacer(Modifier.height(Space.small))
}
