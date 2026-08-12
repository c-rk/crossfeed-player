package dev.crossfeed.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.crossfeed.core.Artwork
import dev.crossfeed.core.LocalLibrary
import dev.crossfeed.core.player.Bucket
import dev.crossfeed.core.player.Category
import dev.crossfeed.core.player.LocalBrowse
import dev.crossfeed.core.player.PlayerEngine
import dev.crossfeed.core.player.Sources
import dev.crossfeed.core.player.Track
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen() {
    val context = LocalContext.current
    val glass = LocalGlass.current

    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Category.SONGS) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var folderRoot by rememberSaveable { mutableStateOf<String?>(null) }

    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var buckets by remember { mutableStateOf(emptyList<Bucket>()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(query, category, openKey) {
        loading = true
        if (query.isNotBlank()) {
            delay(240)
            tracks = Sources.search(context, query, limit = 120)
            buckets = emptyList()
        } else if (category == Category.SONGS) {
            tracks = LocalBrowse.songs(context)
            buckets = emptyList()
        } else if (category == Category.FOLDERS) {
            val root = folderRoot ?: LocalBrowse.folderRoot(context).also { folderRoot = it }
            val (subs, here) = LocalBrowse.folder(context, openKey ?: root)
            buckets = subs
            tracks = here
        } else if (openKey != null) {
            tracks = LocalBrowse.tracksIn(context, category, openKey!!)
            buckets = emptyList()
        } else {
            buckets = LocalBrowse.buckets(context, category)
            tracks = emptyList()
        }
        loading = false
    }

    val columns = if (grid) 3 else 1

    Column(Modifier.fillMaxSize().padding(horizontal = Space.large)) {
        Spacer(Modifier.height(Space.medium))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    openKey?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "player",
                    style = Type.wordmark,
                    color = glass.ink,
                    maxLines = 1,
                )
                Text(
                    when {
                        loading -> "reading your library…"
                        openKey != null -> "${tracks.size} tracks"
                        query.isNotBlank() -> "${tracks.size} results"
                        category == Category.SONGS -> "${tracks.size} tracks"
                        category == Category.FOLDERS ->
                            "${buckets.size} folders · ${tracks.size} tracks"
                        else -> "${buckets.size} ${category.name.lowercase()}"
                    },
                    style = Type.body,
                    color = glass.inkMuted,
                    maxLines = 1,
                )
            }
            LayoutToggle(grid = grid, onToggle = { grid = !grid })
        }

        Spacer(Modifier.height(Space.small))
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "search everything",
            modifier = Modifier.fillMaxWidth(),
        )

        if (query.isBlank()) {
            Spacer(Modifier.height(Space.small))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.tight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (openKey != null) {
                    Chip("‹ back", selected = false) {
                        openKey = if (category == Category.FOLDERS) {
                            val up = openKey!!.substringBeforeLast('/', "")
                            if (up.isBlank() || up.length < (folderRoot?.length ?: 0)) null else up
                        } else {
                            null
                        }
                    }
                }
                for (option in Category.entries) {
                    Chip(option.name.lowercase(), selected = category == option && openKey == null) {
                        category = option
                        openKey = null
                    }
                }
            }
        }

        if (!LocalLibrary.hasPermission(context)) {
            Spacer(Modifier.height(Space.medium))
            Text(
                "crossfeed player cannot read your music yet. grant audio access and come back.",
                style = Type.body,
                color = glass.warning,
            )
        }

        Spacer(Modifier.height(Space.small))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
            verticalArrangement = Arrangement.spacedBy(if (grid) Space.small else 0.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (buckets.isNotEmpty()) {
                items(buckets, key = { "b:${it.key}" }) { bucket ->
                    if (grid) {
                        BucketTile(bucket) { openKey = bucket.key }
                    } else {
                        BucketRow(bucket) { openKey = bucket.key }
                    }
                }
            }
            items(tracks, key = { it.id }) { track ->
                val index = tracks.indexOf(track)
                if (grid) {
                    TrackTile(
                        track = track,
                        onPlay = { PlayerEngine.play(context, tracks, index) },
                        onQueue = { PlayerEngine.addLast(context, track) },
                    )
                } else {
                    TrackRow(
                        track = track,
                        onPlay = { PlayerEngine.play(context, tracks, index) },
                        onQueue = { PlayerEngine.addLast(context, track) },
                    )
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(150.dp)) }
        }
    }
}

@Composable
private fun LayoutToggle(grid: Boolean, onToggle: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(glass.fill)
            .border(1.dp, glass.strokeSoft, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(if (grid) 2 else 3) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(if (grid) 2 else 1) {
                        Box(
                            Modifier
                                .size(if (grid) 6.dp else 14.dp, if (grid) 6.dp else 2.dp)
                                .background(glass.ink),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) glass.accent.copy(alpha = 0.28f) else glass.fill)
            .border(1.dp, if (selected) glass.accent else glass.strokeSoft, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, style = Type.footnote, color = if (selected) glass.ink else glass.inkMuted)
    }
}

@Composable
private fun TrackRow(track: Track, onPlay: () -> Unit, onQueue: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = Space.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArt(track.artwork, track.title, 44.dp)
        Column(Modifier.weight(1f).padding(start = Space.small)) {
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
        IconAction(glyph = Glyph.PLUS, diameter = 30.dp, onClick = onQueue)
    }
}

@Composable
private fun TrackTile(track: Track, onPlay: () -> Unit, onQueue: () -> Unit) {
    val glass = LocalGlass.current
    Column(Modifier.clickable(onClick = onPlay)) {
        Box {
            TrackArt(track.artwork, track.title, null)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp),
            ) {
                IconAction(glyph = Glyph.PLUS, diameter = 26.dp, onClick = onQueue)
            }
        }
        Text(
            track.title,
            style = Type.footnote,
            color = glass.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            track.artist ?: "unknown artist",
            style = Type.caps,
            color = glass.inkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BucketRow(bucket: Bucket, onOpen: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = Space.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackArt(bucket.artwork, bucket.title, 44.dp)
        Column(Modifier.weight(1f).padding(start = Space.small)) {
            Text(
                bucket.title,
                style = Type.callout,
                color = glass.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                bucket.subtitle ?: "${bucket.count} tracks",
                style = Type.footnote,
                color = glass.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("${bucket.count}", style = Type.footnote, color = glass.inkFaint)
    }
}

@Composable
private fun BucketTile(bucket: Bucket, onOpen: () -> Unit) {
    val glass = LocalGlass.current
    Column(Modifier.clickable(onClick = onOpen)) {
        TrackArt(bucket.artwork, bucket.title, null)
        Text(
            bucket.title,
            style = Type.footnote,
            color = glass.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            "${bucket.count} tracks",
            style = Type.caps,
            color = glass.inkFaint,
            maxLines = 1,
        )
    }
}

@Composable
fun TrackArt(url: String?, title: String, size: Dp?) {
    val glass = LocalGlass.current
    val context = LocalContext.current
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        bitmap = url?.let { Artwork.loadUrl(context, it) }?.asImageBitmap()
    }

    val box = if (size != null) Modifier.size(size) else Modifier.fillMaxWidth().aspectRatio(1f)
    Box(
        box
            .clip(Shapes.tile)
            .background(glass.fill),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(title.take(1).uppercase(), style = Type.title, color = glass.inkFaint)
        }
    }
}
