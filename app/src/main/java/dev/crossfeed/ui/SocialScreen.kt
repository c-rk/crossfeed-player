package dev.crossfeed.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.crossfeed.BuildConfig
import dev.crossfeed.core.Artwork
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.Router
import dev.crossfeed.core.history.Stats
import dev.crossfeed.core.net.Account
import dev.crossfeed.core.net.Alert
import dev.crossfeed.core.net.Alerts
import dev.crossfeed.core.net.Circle
import dev.crossfeed.core.net.Notifier
import dev.crossfeed.core.net.Person
import dev.crossfeed.core.net.Post
import dev.crossfeed.core.net.SavedTrack
import dev.crossfeed.core.net.Social
import dev.crossfeed.core.net.Suspension
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SocialLegacy() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val prefs = remember { Prefs(context) }
    val account = remember { Account(context) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    remember { Suspension.load(context) }
    val suspended = Suspension.active

    var handle by remember { mutableStateOf(account.handle.orEmpty()) }
    var registered by remember { mutableStateOf(account.exists && account.claiming == null) }
    var claiming by remember { mutableStateOf(account.claiming) }
    var posts by remember { mutableStateOf(emptyList<Post>()) }
    var circle by remember { mutableStateOf(Circle(emptyList(), emptyList(), emptyList())) }
    var alerts by remember { mutableStateOf(Alerts(emptyList(), 0, 0)) }
    var saves by remember { mutableStateOf(emptyList<SavedTrack>()) }
    var live by remember { mutableStateOf(emptyList<dev.crossfeed.core.net.Live>()) }
    var savedKeys by remember { mutableStateOf(emptySet<String>()) }
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var showAlerts by remember { mutableStateOf(false) }
    var showFeed by remember { mutableStateOf(false) }
    var previewGrid by rememberSaveable { mutableStateOf(true) }
    var sharePlays by remember { mutableStateOf(prefs.sharePlays) }
    var baseUrl by remember { mutableStateOf(prefs.baseUrl) }
    var invite by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<Person?>(null) }

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun react(post: Post, emoji: String?) {
        posts = posts.map { if (it.id == post.id) it.withReaction(emoji) else it }
        scope.launch { runCatching { Social.react(context, post.id, emoji.orEmpty()) } }
    }

    fun save(post: Post) {
        savedKeys = savedKeys + post.id
        scope.launch {
            runCatching { Social.save(context, post) }
                .onSuccess {
                    note = "saved ${post.title}"
                    saves = runCatching { Social.saved(context) }.getOrDefault(saves)
                }
        }
    }

    LaunchedEffect(claiming) {
        if (claiming == null) return@LaunchedEffect
        if (Account.settle(context)) {
            claiming = null
            handle = account.handle.orEmpty()
            registered = true
        }
    }

    LaunchedEffect(registered, reload) {
        if (!registered) return@LaunchedEffect
        runCatching { posts = Social.feed(context) }.onFailure { note = it.message }
        runCatching { circle = Social.circle(context) }
        runCatching { saves = Social.saved(context) }
    }

    LaunchedEffect(registered) {
        if (!registered) return@LaunchedEffect
        Notifier.channel(context)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            askNotify.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(registered, lifecycle) {
        if (!registered) return@LaunchedEffect
        var cursor = 0L
        var quiet = 0
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val result = runCatching { Social.sync(context, cursor) }
                result.getOrNull()?.let { sync ->
                    cursor = sync.now
                    sync.live?.let { live = it }
                    alerts = alerts.copy(unread = sync.unread, requests = sync.requests)
                    for (match in sync.together) {
                        Notifier.together(context, match.handle, match.title, match.key)
                    }
                    if (sync.posts.isNotEmpty()) {
                        quiet = 0
                        val fresh = sync.posts.associateBy { it.id }
                        posts = (sync.posts + posts.filterNot { it.id in fresh.keys })
                            .sortedByDescending { it.updatedAt }
                            .take(60)
                    } else {
                        quiet++
                    }
                }
                delay(if (quiet >= 4) 60_000L else 20_000L)
            }
        }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.large),
    ) {
        Spacer(Modifier.height(Space.medium))
        Text("auxshare", style = Type.wordmark, color = glass.ink)
        Text(
            if (registered) "@${account.handle}" else "what your people are playing",
            style = Type.body,
            color = glass.inkMuted,
        )

        claiming?.let { wanted ->
            Spacer(Modifier.height(Space.medium))
            GlassCard(strong = true) {
                SectionHeader("waiting on @$wanted")
                Text(
                    "that handle already belongs to an account, so crossfeed has asked an admin to " +
                        "release it to you. if they agree, your old posts, saves and connections come " +
                        "back with it.",
                    style = Type.body,
                    color = glass.inkMuted,
                    modifier = Modifier.padding(bottom = Space.small),
                )
                GlassButton(
                    label = "check again",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        scope.launch {
                            if (Account.settle(context)) {
                                claiming = null
                                handle = account.handle.orEmpty()
                                registered = true
                                reload++
                                note = "the handle is yours"
                            } else {
                                note = "still waiting on an admin"
                            }
                        }
                    },
                )
            }
        }

        if (registered && suspended) {
            Spacer(Modifier.height(Space.medium))
            GlassCard(strong = true) {
                SectionHeader("this handle is suspended")
                Text(
                    Suspension.reason ?: "an admin suspended this handle.",
                    style = Type.body,
                    color = glass.warning,
                    modifier = Modifier.padding(bottom = Space.small),
                )
                Text(
                    "the feed and sharing are paused, and nobody sees what you play. " +
                        "your listening history on this phone is untouched. the handle cannot be " +
                        "wiped while it is suspended.",
                    style = Type.footnote,
                    color = glass.inkMuted,
                )
            }
        }

        if (!registered) {
            Spacer(Modifier.height(Space.medium))
            GlassCard {
                SectionHeader("pick a handle")
                Text(
                    "one handle, no password, no email. the phone keeps the key. " +
                        "nothing is shared until you turn sharing on below.",
                    style = Type.footnote,
                    color = glass.inkMuted,
                    modifier = Modifier.padding(bottom = Space.tight),
                )
                Text(
                    "pick it carefully. a handle is permanent, and the only way to change it is " +
                        "to wipe the account and start again.",
                    style = Type.footnote,
                    color = glass.warning,
                    modifier = Modifier.padding(bottom = Space.small),
                )
                SearchField(
                    value = handle,
                    onValueChange = { handle = it },
                    placeholder = "handle",
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Space.small))
                GlassButton(
                    label = if (busy) "creating…" else "create my handle",
                    filled = true,
                    enabled = !busy && handle.trim().length >= 2,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        busy = true
                        note = null
                        scope.launch {
                            runCatching { Account.register(context, handle.trim()) }
                                .onSuccess {
                                    handle = it
                                    claiming = account.claiming
                                    registered = account.claiming == null
                                    if (claiming != null) {
                                        note = "that handle is taken, an admin has been asked to release it"
                                    }
                                }
                                .onFailure { note = it.message }
                            busy = false
                        }
                    },
                )
            }
        }

        note?.let {
            Spacer(Modifier.height(Space.small))
            Text(it, style = Type.footnote, color = glass.inkMuted)
        }

        if (registered && !suspended) {
            Spacer(Modifier.height(Space.medium))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Box {
                    IconAction(glyph = Glyph.BELL, active = alerts.unread > 0) {
                        showAlerts = true
                        scope.launch {
                            runCatching { Social.alerts(context) }
                                .onSuccess { alerts = it }
                                .onFailure { note = it.message }
                            runCatching { Social.markAlertsSeen(context) }
                            alerts = alerts.copy(unread = 0)
                        }
                    }
                    if (alerts.unread > 0) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(15.dp)
                                .clip(CircleShape)
                                .background(glass.warning),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${alerts.unread.coerceAtMost(9)}",
                                style = Type.caps,
                                color = Color.White,
                            )
                        }
                    }
                }
            }

            if (circle.incoming.isNotEmpty()) {
                Spacer(Modifier.height(Space.medium))
                GlassCard {
                    SectionHeader("wants to connect")
                    for (person in circle.incoming) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = Space.tight),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "@${person.handle}",
                                style = Type.headline,
                                color = glass.ink,
                                modifier = Modifier.weight(1f),
                            )
                            GlassButton(label = "no", compact = true, onClick = {
                                scope.launch {
                                    runCatching { Social.respond(context, person.id, false) }
                                    reload++
                                }
                            })
                            Spacer(Modifier.size(Space.tight))
                            GlassButton(label = "yes", compact = true, filled = true, onClick = {
                                scope.launch {
                                    runCatching { Social.respond(context, person.id, true) }
                                    reload++
                                }
                            })
                        }
                    }
                }
            }

            if (live.isNotEmpty()) {
                Spacer(Modifier.height(Space.medium))
                LiveRow(live) { entry ->
                    scope.launch { Router.play(context, entry.title, entry.artist) }
                }
            }

            Spacer(Modifier.height(Space.medium))
            GlassCard {
                SectionHeader("the feed") {
                    Text(
                        "${posts.size}",
                        style = Type.footnote,
                        color = glass.inkFaint,
                        modifier = Modifier.padding(end = Space.tight),
                    )
                    IconAction(
                        glyph = if (previewGrid) Glyph.LIST else Glyph.GRID,
                        diameter = 30.dp,
                        onClick = { previewGrid = !previewGrid },
                    )
                }
                if (posts.isEmpty()) {
                    Text(
                        "nothing yet. add a friend by handle below, or turn sharing on and play something.",
                        style = Type.body,
                        color = glass.inkMuted,
                        modifier = Modifier.padding(vertical = Space.small),
                    )
                }
                if (previewGrid) {
                    val preview = posts.take(GRID_PREVIEW)
                    for (row in preview.chunked(3)) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = Space.tight),
                            horizontalArrangement = Arrangement.spacedBy(Space.tight),
                        ) {
                            for (post in row) {
                                Box(Modifier.weight(1f)) {
                                    PostSquare(
                                        post = post,
                                        onOpen = {
                                            scope.launch { Router.play(context, post.title, post.artist) }
                                        },
                                    )
                                }
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                for (post in if (previewGrid) emptyList() else posts.take(FEED_PREVIEW)) {
                    PostCard(
                        post = post,
                        saved = post.id in savedKeys,
                        onReact = { emoji -> react(post, emoji) },
                        onListen = { scope.launch { Router.play(context, post.title, post.artist) } },
                        onOpen = { scope.launch { Router.play(context, post.title, post.artist) } },
                        onSave = { save(post) },
                        onRemove = if (post.self) {
                            {
                                scope.launch {
                                    runCatching { Social.remove(context, post.id) }
                                    reload++
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                if (posts.size >= FEED_PREVIEW) {
                    Spacer(Modifier.height(Space.small))
                    GlassButton(
                        label = "open the feed",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { showFeed = true },
                    )
                }
            }

            if (saves.isNotEmpty()) {
                Spacer(Modifier.height(Space.medium))
                GlassCard {
                    SectionHeader("saved") {
                        Text("${saves.size}", style = Type.footnote, color = glass.inkFaint)
                    }
                    for (track in saves) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    scope.launch { Router.play(context, track.title, track.artist) }
                                }
                                .padding(vertical = Space.tight),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            UrlArt(track.art, track.title, 40.dp)
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
                                )
                            }
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(glass.fill)
                                    .clickable {
                                        scope.launch {
                                            runCatching { Social.unsave(context, track) }
                                            saves = saves.filterNot { it.title == track.title }
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("×", style = Type.headline, color = glass.inkMuted)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.medium))
            GlassCard {
                SectionHeader("your people")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchField(
                        value = invite,
                        onValueChange = { invite = it },
                        placeholder = "add by handle",
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.size(Space.tight))
                    GlassButton(
                        label = "add",
                        enabled = invite.trim().length >= 2,
                        onClick = {
                            scope.launch {
                                runCatching { Social.request(context, handle = invite.trim()) }
                                    .onSuccess {
                                        note = if (it == "accepted") "connected" else "request sent"
                                        invite = ""
                                    }
                                    .onFailure { error -> note = error.message }
                                reload++
                            }
                        },
                    )
                }
                for (person in circle.accepted) {
                    PersonRow(person, "connected") { removing = person }
                }
                for (person in circle.outgoing) {
                    PersonRow(person, "waiting") {
                        scope.launch {
                            runCatching { Social.unfriend(context, person.id) }
                            reload++
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(110.dp))
    }

    if (showFeed) {
        FeedScreen(
            first = posts,
            savedKeys = savedKeys,
            onClose = { showFeed = false },
            onReact = { post, emoji ->
                react(post, emoji)
                post.withReaction(emoji)
            },
            onSave = { save(it) },
            onRemoved = { reload++ },
        )
    }

    if (showAlerts) {
        AlertsDialog(
            alerts = alerts,
            onPlay = { alert -> scope.launch { Router.play(context, alert.title, alert.artist) } },
            onClose = { showAlerts = false },
        )
    }

    removing?.let { person ->
        ConfirmRemove(
            handle = person.handle,
            onCancel = { removing = null },
            onConfirm = {
                removing = null
                scope.launch {
                    runCatching { Social.unfriend(context, person.id) }
                    note = "removed @${person.handle}"
                    reload++
                }
            },
        )
    }

}

/** Removing someone is quiet and immediate, so it is worth one question first. */
@Composable
private fun ConfirmRemove(handle: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val glass = LocalGlass.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onCancel) {
        GlassCard(strong = true) {
            Text("remove @$handle?", style = Type.title, color = glass.ink)
            Text(
                "everything they played leaves your feed straight away, and everything you played " +
                    "leaves theirs. reactions they have already left on your songs stay. neither " +
                    "of you is told, and they can ask to connect again.",
                style = Type.footnote,
                color = glass.inkMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = Space.medium),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                GlassButton(label = "keep", modifier = Modifier.weight(1f), onClick = onCancel)
                GlassButton(
                    label = "remove",
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onConfirm,
                )
            }
        }
    }
}

@Composable
private fun AlertsDialog(alerts: Alerts, onPlay: (Alert) -> Unit, onClose: () -> Unit) {
    val glass = LocalGlass.current
    val posts = remember(alerts.items) { alerts.items.groupBy { it.postId } }

    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        GlassCard(strong = true) {
            SectionHeader("reactions") {
                Text("${alerts.items.size}", style = Type.footnote, color = glass.inkFaint)
            }
            if (posts.isEmpty()) {
                Text(
                    "nobody has reacted yet. turn sharing on and play something, and reactions " +
                        "to your songs will land here.",
                    style = Type.body,
                    color = glass.inkMuted,
                    modifier = Modifier.padding(vertical = Space.small),
                )
            }
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for ((_, group) in posts) {
                    val head = group.first()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPlay(head) }
                            .padding(vertical = Space.small),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            UrlArt(head.art, head.title, 46.dp)
                            Column(Modifier.weight(1f).padding(start = Space.small)) {
                                Text(
                                    head.title,
                                    style = Type.callout,
                                    color = if (group.any { it.fresh }) glass.ink else glass.inkMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    head.artist ?: "unknown artist",
                                    style = Type.footnote,
                                    color = glass.inkFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(ago(head.at), style = Type.footnote, color = glass.inkFaint)
                            }
                            if (group.any { it.fresh }) {
                                Box(
                                    Modifier
                                        .size(9.dp)
                                        .clip(CircleShape)
                                        .background(glass.warning),
                                )
                            }
                        }
                        Row(
                            Modifier.padding(top = Space.tight),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            for (alert in group.take(8)) {
                                Row(
                                    Modifier
                                        .clip(CircleShape)
                                        .background(glass.fill)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(alert.emoji, style = Type.footnote)
                                    Text(
                                        " @${alert.handle}",
                                        style = Type.footnote,
                                        color = glass.inkMuted,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(Space.small))
            GlassButton(label = "done", filled = true, modifier = Modifier.fillMaxWidth(), onClick = onClose)
        }
    }
}

@Composable
private fun PersonRow(person: Person, state: String, onRemove: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("@${person.handle}", style = Type.headline, color = glass.ink)
            Text(state, style = Type.footnote, color = glass.inkFaint)
        }
        Box(
            Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(glass.fill)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Text("×", style = Type.headline, color = glass.inkMuted)
        }
    }
}


/** A tile in the collapsed feed: the sleeve does the work, the handle says whose it is. */
@Composable
private fun PostSquare(post: Post, onOpen: () -> Unit) {
    val glass = LocalGlass.current
    Column(Modifier.clickable(onClick = onOpen)) {
        Box(Modifier.clip(Shapes.tile)) {
            TrackArt(post.art, post.title, null)
        }
        Text(
            post.title,
            style = Type.footnote,
            color = glass.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(
            "@" + post.handle,
            style = Type.caps,
            color = glass.inkFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PostCard(
    post: Post,
    saved: Boolean,
    onReact: (String?) -> Unit,
    onListen: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val glass = LocalGlass.current
    Column(Modifier.fillMaxWidth().padding(vertical = Space.tight)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UrlArt(post.art, post.title, 46.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clickable(onClick = onOpen)
                    .padding(start = Space.small, end = Space.tight),
            ) {
                Text(
                    post.title,
                    style = Type.callout,
                    color = glass.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    post.artist ?: "unknown artist",
                    style = Type.footnote,
                    color = glass.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    post.source?.let {
                        SourceIcon(it, size = 12.dp)
                        Spacer(Modifier.size(4.dp))
                    }
                    Text(
                        "@${post.handle} · ${ago(post.updatedAt)} · ${Stats.minutes(post.listenedMs)}" +
                            if (post.reactions > 0) " · ${post.reactions}" else "",
                        style = Type.footnote,
                        color = glass.inkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconAction(glyph = Glyph.PLAY, filled = true, diameter = 34.dp, onClick = onListen)
            Spacer(Modifier.size(Space.tight))
            IconAction(glyph = Glyph.BOOKMARK, active = saved, diameter = 34.dp, onClick = onSave)
            onRemove?.let {
                Spacer(Modifier.size(Space.tight))
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(glass.fill)
                        .clickable(onClick = it),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("×", style = Type.callout, color = glass.inkMuted)
                }
            }
        }
        Spacer(Modifier.height(Space.tight))
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (emoji in Social.emojis) {
                Reaction(
                    emoji = emoji,
                    count = post.counts[emoji] ?: 0,
                    active = post.mine == emoji,
                ) { onReact(if (post.mine == emoji) null else emoji) }
            }
        }
    }
}

@Composable
private fun Reaction(emoji: String, count: Int, active: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .clip(CircleShape)
            .background(if (active) glass.accent.copy(alpha = 0.35f) else glass.fill)
            .border(1.dp, if (active) glass.accent else glass.strokeSoft, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, style = Type.callout, color = glass.ink)
        if (count > 0) {
            Text(
                "$count",
                style = Type.footnote,
                color = if (active) glass.ink else glass.inkMuted,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
fun UrlArt(url: String?, title: String, size: Dp, round: Boolean = false) {
    val glass = LocalGlass.current
    val shape = if (round) CircleShape else Shapes.tile
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = url?.let { Artwork.loadRemote(it) }?.asImageBitmap()
    }
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(glass.fill)
            .border(1.dp, glass.strokeSoft, shape),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(shape),
            )
        } else {
            Text(title.take(1).uppercase(), style = Type.headline, color = glass.inkFaint)
        }
    }
}


@Composable
private fun LiveRow(entries: List<dev.crossfeed.core.net.Live>, onPlay: (dev.crossfeed.core.net.Live) -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Space.small),
    ) {
        for (entry in entries) {
            Column(
                Modifier
                    .width(94.dp)
                    .clickable { onPlay(entry) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(glass.hot))
                        .padding(2.5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(59.dp)
                            .clip(CircleShape)
                            .background(glass.tile),
                        contentAlignment = Alignment.Center,
                    ) {
                        UrlArt(entry.art, entry.title, 55.dp, round = true)
                    }
                }
                Text(
                    if (entry.self) "you" else "@${entry.handle}",
                    style = Type.footnote,
                    color = glass.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    entry.title,
                    style = Type.footnote,
                    color = glass.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.artist ?: "unknown artist",
                    style = Type.caps,
                    color = glass.inkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun ago(millis: Long): String {
    val gap = System.currentTimeMillis() - millis
    return when {
        gap < 60_000 -> "now"
        gap < 3_600_000 -> "${gap / 60_000}m ago"
        gap < 86_400_000 -> "${gap / 3_600_000}h ago"
        else -> "${gap / 86_400_000}d ago"
    }
}

private const val FEED_PREVIEW = 4

/** Tiles come three to a row, so four of them left an orphan under a full row. */
private const val GRID_PREVIEW = 6
