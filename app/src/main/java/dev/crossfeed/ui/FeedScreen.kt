package dev.crossfeed.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.crossfeed.core.Artwork
import dev.crossfeed.core.Router
import dev.crossfeed.core.net.Post
import dev.crossfeed.core.net.Social
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun FeedScreen(
    first: List<Post>,
    savedKeys: Set<String>,
    onClose: () -> Unit,
    onReact: (Post, String?) -> Post,
    onSave: (Post) -> Unit,
    onRemoved: () -> Unit,
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var posts by remember { mutableStateOf(first) }
    var loading by remember { mutableStateOf(false) }
    var exhausted by remember { mutableStateOf(first.size < Social.PAGE) }
    var saved by remember { mutableStateOf(savedKeys) }
    var openId by remember { mutableStateOf<String?>(null) }
    var pickerId by remember { mutableStateOf<String?>(null) }
    var grid by rememberSaveable { mutableStateOf(true) }

    val nearEnd by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= posts.size - 6
        }
    }

    LaunchedEffect(nearEnd, exhausted) {
        if (!nearEnd || exhausted || loading || posts.isEmpty()) return@LaunchedEffect
        loading = true
        val older = runCatching { Social.feed(context, before = posts.last().updatedAt) }
            .getOrDefault(emptyList())
        if (older.isEmpty()) {
            exhausted = true
        } else {
            val known = posts.map { it.id }.toSet()
            posts = posts + older.filterNot { it.id in known }
            if (older.size < Social.PAGE) exhausted = true
        }
        loading = false
    }

    fun apply(post: Post, emoji: String?) {
        val updated = onReact(post, emoji)
        posts = posts.map { if (it.id == post.id) updated else it }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(glass.wash)),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = Space.medium),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "the feed",
                        style = Type.wordmark,
                        color = glass.ink,
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                    )
                    IconAction(
                        glyph = if (grid) Glyph.LIST else Glyph.GRID,
                        diameter = 36.dp,
                        onClick = { grid = !grid },
                    )
                    Spacer(Modifier.width(Space.tight))
                    GlassButton(label = "close", compact = true, onClick = onClose)
                }
                Text(
                    "double tap to love · hold for more · tap for play and save",
                    style = Type.footnote,
                    color = glass.inkFaint,
                    modifier = Modifier.padding(start = 6.dp, bottom = Space.small),
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (grid) 3 else 1),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(Space.tight),
                    verticalArrangement = Arrangement.spacedBy(Space.small),
                    modifier = Modifier.weight(1f),
                ) {
                    items(posts, key = { it.id }) { post ->
                        if (!grid) {
                            PostCard(
                                post = post,
                                saved = post.id in saved,
                                onReact = { emoji -> apply(post, emoji) },
                                onListen = {
                                    scope.launch { Router.play(context, post.title, post.artist) }
                                },
                                onOpen = {
                                    scope.launch { Router.play(context, post.title, post.artist) }
                                },
                                onSave = {
                                    saved = saved + post.id
                                    onSave(post)
                                },
                                onRemove = if (post.self) {
                                    {
                                        scope.launch {
                                            runCatching { Social.remove(context, post.id) }
                                            posts = posts.filterNot { it.id == post.id }
                                            onRemoved()
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                            return@items
                        }
                        PostTile(
                            post = post,
                            saved = post.id in saved,
                            open = openId == post.id,
                            picking = pickerId == post.id,
                            onTap = { openId = if (openId == post.id) null else post.id },
                            onDoubleTap = { apply(post, if (post.mine == "❤️") null else "❤️") },
                            onHold = { pickerId = post.id },
                            onPick = { emoji ->
                                pickerId = null
                                apply(post, emoji)
                            },
                            onDismissPicker = { pickerId = null },
                            onListen = { scope.launch { Router.play(context, post.title, post.artist) } },
                            onSave = {
                                saved = saved + post.id
                                onSave(post)
                            },
                            onRemove = if (post.self) {
                                {
                                    scope.launch {
                                        runCatching { Social.remove(context, post.id) }
                                        posts = posts.filterNot { it.id == post.id }
                                        onRemoved()
                                    }
                                }
                            } else {
                                null
                            },
                        )
                    }
                }

                Text(
                    when {
                        loading -> "loading…"
                        exhausted -> "that is everything"
                        else -> " "
                    },
                    style = Type.footnote,
                    color = glass.inkFaint,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Space.small),
                )
            }
        }
    }
}

@Composable
private fun PostTile(
    post: Post,
    saved: Boolean,
    open: Boolean,
    picking: Boolean,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onHold: () -> Unit,
    onPick: (String?) -> Unit,
    onDismissPicker: () -> Unit,
    onListen: () -> Unit,
    onSave: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val glass = LocalGlass.current
    val context = LocalContext.current
    var bitmap by remember(post.art) { mutableStateOf<ImageBitmap?>(null) }
    var pulse by remember { mutableStateOf(false) }

    LaunchedEffect(post.art) {
        bitmap = post.art?.let { Artwork.loadRemote(it) }?.asImageBitmap()
    }
    LaunchedEffect(pulse) {
        if (pulse) {
            delay(700)
            pulse = false
        }
    }
    val heart by animateFloatAsState(
        targetValue = if (pulse) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "heart",
    )

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(Shapes.tile)
                .background(glass.fill)
                .border(1.dp, glass.strokeSoft, Shapes.tile)
                .pointerInput(post.id) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            pulse = true
                            onDoubleTap()
                        },
                        onLongPress = { onHold() },
                    )
                },
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
                Text(post.title.take(1).uppercase(), style = Type.title, color = glass.inkFaint)
            }

            if (post.mine != null && !open && !picking) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(post.mine, style = Type.footnote)
                }
            }

            if (!open && !picking) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(ago(post.updatedAt), style = Type.footnote, color = Color.White)
                }
            }

            if (post.reactions > 0 && !open && !picking) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text("${post.reactions}", style = Type.footnote, color = Color.White)
                }
            }

            if (heart > 0f) {
                Text(
                    "❤️",
                    style = Type.wordmark,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.6f + heart * 0.7f
                        scaleY = 0.6f + heart * 0.7f
                        alpha = heart
                    },
                )
            }

            if (open) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                        IconAction(glyph = Glyph.PLAY, filled = true, diameter = 36.dp, onClick = onListen)
                        IconAction(glyph = Glyph.BOOKMARK, active = saved, diameter = 36.dp, onClick = onSave)
                    }
                    onRemove?.let { remove ->
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                                .pointerInput(post.id) {
                                    detectTapGestures(onTap = { remove() })
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("×", style = Type.headline, color = Color.White)
                        }
                    }
                }
            }

            if (picking) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.62f))
                        .pointerInput(post.id) {
                            detectTapGestures(onTap = { onDismissPicker() })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        for (row in Social.emojis.chunked(3)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (emoji in row) {
                                    Box(
                                        Modifier
                                            .padding(3.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (post.mine == emoji) {
                                                    glass.accent.copy(alpha = 0.5f)
                                                } else {
                                                    Color.White.copy(alpha = 0.14f)
                                                },
                                            )
                                            .pointerInput(emoji) {
                                                detectTapGestures(
                                                    onTap = {
                                                        onPick(if (post.mine == emoji) null else emoji)
                                                    },
                                                )
                                            }
                                            .padding(7.dp),
                                    ) {
                                        Text(emoji, style = Type.headline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text(
            post.title,
            style = Type.footnote,
            color = glass.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            post.source?.let {
                SourceIcon(it, size = 11.dp)
                Spacer(Modifier.size(3.dp))
            }
            Text(
                "@${post.handle}",
                style = Type.callout,
                color = glass.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}
