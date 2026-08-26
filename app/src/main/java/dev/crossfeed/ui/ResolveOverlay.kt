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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import dev.crossfeed.core.EntityKind
import dev.crossfeed.core.LibraryItem
import dev.crossfeed.core.Opener
import dev.crossfeed.core.Platform
import dev.crossfeed.core.PlaylistMeta
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.RecentEntry
import dev.crossfeed.core.Resolved
import dev.crossfeed.core.Resolver
import dev.crossfeed.core.Route
import dev.crossfeed.core.TrackMeta
import dev.crossfeed.core.catalog.Catalog
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        text = "open it, or copy the link for any of these",
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

    // a shared song is usually on its way to someone else, so every service is offered rather
    // than only the ones this phone opens links in.
    //
    // a search stands in for each of them until the catalogues answer, since the rows should be
    // there to read straight away, and any that can be pinned to the record itself then are
    fun searches() = Platform.entries.map { platform ->
        outcome.routes.firstOrNull { it.platform == platform }
            ?: if (platform == outcome.source) {
                Route(platform, outcome.sourceUrl, true)
            } else {
                Route(platform, platform.searchUrl(meta.query, Prefs(context).country), false)
            }
    }

    var everywhere by remember(outcome.sourceUrl) { mutableStateOf(searches()) }

    LaunchedEffect(outcome.sourceUrl) {
        everywhere = withContext(Dispatchers.IO) {
            coroutineScope {
                searches().map { route ->
                    async {
                        if (route.exact) {
                            route
                        } else {
                            val address = Catalog.address(context, route.platform, meta)
                            Route(route.platform, address.url, address.exact)
                        }
                    }
                }.awaitAll()
            }
        }
    }

    for (route in everywhere) {
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

    if (meta.kind == EntityKind.PLAYLIST || meta.kind == EntityKind.ALBUM) {
        PlaylistTracks(outcome.sourceUrl)
    }

    Spacer(Modifier.height(Space.small))
}

/**
 * A shared playlist is a list of songs, and each of them is shareable in its own right, so the
 * running order is opened up rather than treated as one opaque link.
 */
@Composable
private fun PlaylistTracks(url: String) {
    val glass = LocalGlass.current
    var tracks by remember(url) { mutableStateOf<List<TrackMeta>?>(null) }
    var linksFor by remember { mutableStateOf<TrackMeta?>(null) }

    LaunchedEffect(url) {
        tracks = withContext(Dispatchers.IO) {
            runCatching { PlaylistMeta.tracks(url) }.getOrDefault(emptyList())
        }
    }

    val found = tracks
    Spacer(Modifier.height(Space.small))
    when {
        found == null -> Text("reading the list…", style = Type.footnote, color = glass.inkMuted)
        found.isEmpty() -> Text(
            "could not read the songs in this one, but the links above still work",
            style = Type.footnote,
            color = glass.inkFaint,
        )
        else -> {
            Text(
                found.size.toString() + " songs · tap one for its links",
                style = Type.caps,
                color = glass.inkMuted,
                modifier = Modifier.padding(bottom = Space.tight),
            )
            Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                for (track in found) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { linksFor = track }
                            .padding(vertical = Space.tight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = Type.callout,
                                color = glass.ink,
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
                        IconAction(glyph = Glyph.LINK, diameter = 30.dp) { linksFor = track }
                    }
                }
            }
        }
    }

    linksFor?.let { track ->
        LinksSheet(title = track.title, artist = track.artist, onDismiss = { linksFor = null })
    }
}
