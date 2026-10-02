package dev.crossfeed.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.Router
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import dev.crossfeed.core.net.Circle
import dev.crossfeed.core.net.Person
import dev.crossfeed.core.net.Account
import dev.crossfeed.core.net.Alerts
import dev.crossfeed.core.net.Live
import dev.crossfeed.core.net.Post
import dev.crossfeed.core.net.Social
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Four reactions, always the same four, always in this order.
 *
 * The old drawer of faces asked people to pick a mood off a keyboard. These say something instead,
 * and each one does a different thing: two are opinions and one is an action.
 */
private data class Reaction(val emoji: String, val label: String)

private val reactions = listOf(
    Reaction("🔥", "a banger"),
    Reaction("🎧", "on it now"),
    Reaction("💾", "keeping it"),
)

/**
 * The aux.
 *
 * Everything on this page belongs to someone else, which is why the light behind it is sage and
 * why the notifications never take you anywhere: the tray opens in place, says its piece, and
 * closes again.
 */
@Composable
fun SocialScreen(visible: Boolean = true) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val account = remember { Account(context) }
    val prefs = remember { Prefs(context) }

    var joining by remember { mutableStateOf(false) }

    // the aux needs a handle, and this build has its own, separate from any other copy of the app
    // on this phone. saying so plainly beats an empty feed that looks like nobody is posting
    if (!account.exists || account.claiming != null || joining) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.large),
        ) {
            Spacer(Modifier.height(Space.medium))
            Text("the aux", style = Type.page, color = glass.t1)
            Text(
                if (account.claiming != null) {
                    "waiting on the handle @" + account.claiming + " to be handed over."
                } else {
                    "this copy of crossfeed has no handle yet, so there is no feed to show and " +
                        "nobody listening. sign in below."
                },
                style = Type.note,
                color = glass.t3,
                modifier = Modifier.padding(top = 4.dp, bottom = Space.small),
            )
            SocialLegacy(scrollable = false)
        }
        return
    }

    val posts = Aux.posts
    val live = Aux.live
    val alerts = Aux.alerts
    val circle = Aux.circle
    var grid by remember { mutableStateOf(prefs.auxGrid) }
    var trayOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf(prefs.sharePlays) }
    var chosen by remember { mutableStateOf<String?>(null) }
    // someone who left the aux takes their filter with them
    val only = chosen?.takeIf { handle -> circle.accepted.any { it.handle == handle } }
    val pick = { handle: String -> chosen = if (only == handle) null else handle }
    val shown = only?.let { handle -> posts.filter { it.handle == handle } } ?: posts

    /*
     * Keeping current costs somebody else's database, so it only happens when it is worth
     * something: while this page is the one on screen, and while the app is actually in front of
     * you. All four pages stay composed so swiping is instant, which meant this loop used to run
     * for hours against a page nobody was looking at.
     */
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(visible, owner) {
        if (!visible) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // the app this replaces already had the right shape here and I wrote a worse one:
            // it backs off when nothing is happening, so an aux left open on a quiet afternoon
            // asks once a minute rather than four times
            var quiet = 0
            while (true) {
                if (Aux.refresh(context)) quiet = 0 else quiet++
                delay(if (quiet >= 4) 60_000L else 20_000L)
            }
        }
    }

    fun react(post: Post, reaction: Reaction) {
        val next = if (post.mine == reaction.emoji) null else reaction.emoji
        Aux.replace(
            posts.map { if (it.id == post.id) it.withReaction(next) else it },
            pending = post.id,
        )
        picking = null
        scope.launch {
            runCatching { Social.react(context, post.id, next.orEmpty()) }
            Aux.settled(post.id)
            // keeping it does what it says as well as saying it
            if (reaction.label == "keeping it" && next != null) {
                runCatching { Social.save(context, post) }
                note = "kept ${post.title}"
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 3 else 1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.large, end = Space.large, top = Space.small, bottom = bottomRoom()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(if (grid) 8.dp else 0.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("the aux", style = Type.page, color = glass.t1)
                        Text(
                            "@" + account.handle.orEmpty() + " · " + people(circle.accepted.size),
                            style = Type.note,
                            color = glass.t3,
                        )
                    }
                    UnreadCircle(alerts.unread) {
                        trayOpen = !trayOpen
                        if (trayOpen) {
                            Aux.seen()
                            scope.launch {
                                Aux.openTray(context)
                                runCatching { Social.markAlertsSeen(context) }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Space.small))
                Rise(trayOpen) { Tray(alerts) { trayOpen = false } }
                if (trayOpen) Spacer(Modifier.height(Space.small))

                if (live.isNotEmpty()) {
                    Listening(live, only, pick)
                } else {
                    Text(
                        "nobody on the aux has anything playing right now.",
                        style = Type.note,
                        color = glass.t3,
                    )
                }
                Spacer(Modifier.height(Space.small))

                People(
                    circle = circle,
                    only = only,
                    onPick = pick,
                    onAdd = { handle ->
                        scope.launch {
                            note = runCatching { Social.request(context, handle = handle) }
                                .map { "asked @" + handle.removePrefix("@") }
                                .getOrElse { it.message ?: "could not ask for that handle" }
                            Aux.refresh(context)
                        }
                    },
                    onRespond = { person, accept ->
                        scope.launch {
                            runCatching { Social.respond(context, person.id, accept) }
                            Aux.refresh(context)
                        }
                    },
                )
                Spacer(Modifier.height(Space.small))

                // an aux nobody is putting anything on is not broken, it is silent, and the
                // difference was invisible: sharing is off until it is asked for, so a feed can
                // sit empty for days looking like a fault
                if (!sharing) {
                    SageCard(padding = Space.medium) {
                        Text("you are not on the aux", style = Type.section, color = glass.t1)
                        Text(
                            "nothing you play is being shared, so your people see an empty feed " +
                                "and so do you. the track and how long, never the diary.",
                            style = Type.note,
                            color = glass.t2,
                            modifier = Modifier.padding(top = 4.dp, bottom = Space.small),
                        )
                        GlassButton(label = "start sharing", filled = true, compact = true) {
                            prefs.sharePlays = true
                            sharing = true
                            note = "sharing from the next song on"
                        }
                    }
                    Spacer(Modifier.height(Space.small))
                }

                Aux.trouble?.let {
                    // out of touch rather than empty: the page keeps showing the last thing it
                    // knew, and says how old that is instead of pretending it is now
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = Space.tight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            it,
                            style = Type.note,
                            color = glass.warning,
                            modifier = Modifier.weight(1f),
                        )
                        Aux.loadedAt.takeIf { at -> at > 0 }?.let { at ->
                            Text(
                                "as of " + age(at),
                                style = Type.metaStrong,
                                color = glass.t3,
                                maxLines = 1,
                            )
                        }
                    }
                }
                note?.let {
                    Text(it, style = Type.note, color = glass.sage, modifier = Modifier.padding(bottom = Space.tight))
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = Space.tight, bottom = Space.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("the feed", style = Type.section, color = glass.t1)
                    Spacer(Modifier.width(Space.tight))
                    Text(today(shown), style = Type.stamp, color = glass.t3, modifier = Modifier.weight(1f))
                    only?.let {
                        Row(
                            Modifier
                                .clip(Shapes.chip)
                                .background(glass.sage)
                                .clickable { chosen = null }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text("@$it", style = Type.metaStrong, color = glass.onSage, maxLines = 1)
                            Mark(Glyph.CLOSE, side = 8.dp, tint = glass.onSage)
                        }
                        Spacer(Modifier.width(Space.tight))
                    }
                    ViewToggle(grid) {
                        grid = it
                        prefs.auxGrid = it
                    }
                }
                if (shown.isNotEmpty()) {
                    Text(
                        "tap to hear it · double tap for a banger · hold for the rest",
                        style = Type.meta,
                        color = glass.t3,
                        modifier = Modifier.padding(bottom = Space.tight),
                    )
                }
            }
        }

        if (shown.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    when {
                        only != null && posts.isNotEmpty() -> "nothing from @$only in the feed lately."
                        Aux.trouble != null -> Aux.trouble.orEmpty()
                        Aux.loadedAt == 0L -> "reading the aux\u2026"
                        circle.accepted.isEmpty() -> "nobody on your aux yet. add someone by handle above."
                        else -> "nobody has played anything since you connected. the feed starts " +
                            "from the moment you added each other, never before it."
                    },
                    style = Type.note,
                    color = if (Aux.trouble != null) glass.warning else glass.t3,
                )
            }
        }

        items(shown, key = { it.id }) { post ->
            val holding = picking == post.id
            val onHold = { picking = if (picking == post.id) null else post.id }
            // the quick one: two taps says the thing most people want to say, without a menu
            val onLove = { react(post, reactions.first()) }
            // a tap is the plain thing a tap should be: hear it, wherever your route points
            val onOpen = { scope.launch { Router.play(context, post.title, post.artist) }; Unit }
            val onPick = { reaction: Reaction -> react(post, reaction) }
            val onLet = { picking = null }
            if (grid) {
                FeedTile(post, holding, onOpen, onHold, onLove, onPick, onLet)
            } else {
                FeedRow(post, holding, onOpen, onHold, onLove, onPick, onLet)
            }
        }

    }
}

@Composable
private fun UnreadCircle(count: Int, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(glass.sage)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("$count", style = Type.section, color = glass.onSage)
    }
}

/** Notifications, opened where they are. Never a screen, never a dialog. */
@Composable
private fun Tray(alerts: Alerts, onClear: () -> Unit) {
    val glass = LocalGlass.current
    GlassCard(padding = 15.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("the tray", style = Type.section, color = glass.t1, modifier = Modifier.weight(1f))
            Text(
                "CLEAR",
                style = Type.tagWide,
                color = glass.t3,
                modifier = Modifier.clip(Shapes.chip).clickable(onClick = onClear).padding(4.dp),
            )
        }
        if (alerts.items.isEmpty()) {
            Spacer(Modifier.height(Space.small))
            Text("nothing waiting.", style = Type.note, color = glass.t3)
        }

        for (alert in alerts.items.take(6)) {
            Spacer(Modifier.height(Space.small))
            RowRule()
            Row(
                Modifier.fillMaxWidth().padding(top = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Sleeve(alert.handle, alert.art, 34.dp, CircleShape)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "@" + alert.handle + " " + saidOf(alert.emoji) + " " + alert.title,
                        style = Type.chip,
                        color = glass.t2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(age(alert.at), style = Type.meta, color = glass.t3)
                }
            }
        }
    }
}

/** Who else has something on right now, as a ring around each of them. */
@Composable
private fun Listening(live: List<Live>, only: String?, onPick: (String) -> Unit) {
    val glass = LocalGlass.current
    val ring = glass.sage
    val rest = glass.t1.copy(alpha = 0.13f)

    // a clock of its own, so the rings move between the times anyone speaks
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            clock = System.currentTimeMillis()
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Space.medium)) {
        for (person in live.take(3)) {
            Column(
                Modifier
                    .width(70.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPick(person.handle) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    // the ring is the track: sage as far as they have got, faint for the rest,
                    // starting at twelve and going round the way a record does
                    Canvas(Modifier.size(60.dp)) {
                        val width = 3.dp.toPx()
                        val inset = width / 2f
                        val box = Size(size.width - width, size.height - width)
                        drawArc(
                            color = rest,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = box,
                            style = Stroke(width = width, cap = StrokeCap.Round),
                        )
                        val sweep = person.throughAt(clock)
                        if (sweep > 0f) {
                            drawArc(
                                color = ring,
                                startAngle = -90f,
                                sweepAngle = 360f * sweep,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = box,
                                style = Stroke(width = width, cap = StrokeCap.Round),
                            )
                        }
                    }
                    Box(
                        Modifier.size(51.dp).clip(CircleShape).background(glass.p3),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            person.handle.take(1).uppercase(),
                            style = Type.sectionLarge,
                            color = glass.t1,
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    "@" + person.handle,
                    style = Type.metaStrong,
                    color = if (only == person.handle) glass.sage else glass.t1,
                    maxLines = 1,
                )
                Text(
                    person.title,
                    style = Type.meta,
                    color = glass.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedRow(
    post: Post,
    holding: Boolean,
    onOpen: () -> Unit,
    onHold: () -> Unit,
    onLove: () -> Unit,
    onPick: (Reaction) -> Unit,
    onLet: () -> Unit,
) {
    val glass = LocalGlass.current
    Column(
        Modifier.combinedClickable(
            onClick = onOpen,
            onDoubleClick = onLove,
            onLongClick = onHold,
        ),
    ) {
        RowRule()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sleeve(post.title, post.art, 40.dp, Shapes.artRow)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(post.title, style = Type.rowTitle, color = glass.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(post.artist, "@" + post.handle, age(post.updatedAt)).joinToString(" · "),
                    style = Type.meta,
                    color = glass.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // how long they stayed with it. on its own line, because the line above is already
                // three things long and a name can be any length at all
                listenedFor(post.listenedMs)?.let {
                    Text("listened $it", style = Type.stamp, color = glass.t3, maxLines = 1)
                }
            }
            Spacer(Modifier.width(Space.tight))
            Bubbles(post)
        }
        Held(holding, post, labels = true, onPick = onPick, onLet = onLet)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeedTile(
    post: Post,
    holding: Boolean,
    onOpen: () -> Unit,
    onHold: () -> Unit,
    onLove: () -> Unit,
    onPick: (Reaction) -> Unit,
    onLet: () -> Unit,
) {
    val glass = LocalGlass.current
    GlassCard(
        shape = Shapes.grid,
        padding = 7.dp,
        modifier = Modifier.combinedClickable(
            onClick = onOpen,
            onDoubleClick = onLove,
            onLongClick = onHold,
        ),
    ) {
        Sleeve(post.title, post.art, 0.dp, Shapes.artSmall, fill = true)
        Spacer(Modifier.height(5.dp))
        Text(post.title, style = Type.metaStrong, color = glass.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "@" + post.handle + " · " + age(post.updatedAt),
            style = Type.meta,
            color = glass.t3,
            maxLines = 1,
        )
        listenedFor(post.listenedMs)?.let {
            Text("listened $it", style = Type.stamp, color = glass.t3, maxLines = 1)
        }
        Spacer(Modifier.height(5.dp))
        Bubbles(post)
        Held(holding, post, labels = false, onPick = onPick, onLet = onLet)
    }
}

/** Who reacted, as overlapping discs, and how many in all. */
@Composable
private fun Bubbles(post: Post) {
    val glass = LocalGlass.current
    val shown = post.counts.keys.take(3)
    if (shown.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        // the discs overlap, so a cluster reads as a group rather than as a list
        Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
            for (emoji in shown) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(glass.p3)
                        .border(1.5.dp, glass.bg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(emoji, style = Type.meta, color = glass.t1)
                }
            }
        }
        Spacer(Modifier.width(7.dp))
        Text("${post.reactions}", style = Type.metaStrong, color = glass.t3)
    }
}

/**
 * The three, over the row you are holding.
 *
 * It used to be a card at the foot of the feed: a screenful of chrome, appearing nowhere near the
 * thing it was about, opened by a tap that also meant other things. Holding a row is the gesture
 * every phone already teaches, it costs no layout at all, and the bubbles sit on the record they
 * belong to.
 */
@Composable
private fun Held(
    open: Boolean,
    post: Post,
    labels: Boolean,
    onPick: (Reaction) -> Unit,
    onLet: () -> Unit,
) {
    val glass = LocalGlass.current
    Rise(open) {
        Row(
            Modifier
                .padding(top = 6.dp, bottom = 4.dp)
                .clip(Shapes.chip)
                .background(glass.p3)
                .border(1.dp, glass.bd, Shapes.chip)
                .padding(horizontal = if (labels) 6.dp else 3.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(if (labels) 4.dp else 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (reaction in reactions) {
                val mine = post.mine == reaction.emoji
                Row(
                    Modifier
                        .clip(Shapes.chip)
                        .background(if (mine) glass.accent else Color.Transparent)
                        .clickable { onPick(reaction) }
                        .padding(
                            horizontal = if (labels) 9.dp else 7.dp,
                            vertical = if (labels) 6.dp else 5.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(reaction.emoji, style = Type.chip)
                    if (labels) {
                        Text(
                            reaction.label,
                            style = Type.metaStrong,
                            color = if (mine) glass.onAccent else glass.t3,
                            maxLines = 1,
                        )
                    }
                }
            }
            if (labels) {
                Box(
                    Modifier
                        .clip(Shapes.chip)
                        .clickable(onClick = onLet)
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                ) {
                    Mark(Glyph.CLOSE, side = 10.dp, tint = glass.t3)
                }
            }
        }
    }
}

private fun people(count: Int): String = when (count) {
    0 -> "nobody yet"
    1 -> "one person"
    else -> "$count people"
}

private fun today(posts: List<Post>): String {
    val since = System.currentTimeMillis() - 86_400_000
    return "${posts.count { it.updatedAt >= since }} today"
}

private fun saidOf(emoji: String): String = when (emoji) {
    "🔥" -> "called it a banger:"
    "🎧" -> "is on"
    "💾" -> "is keeping"
    "↩" -> "answered with"
    else -> "reacted to"
}

private fun age(at: Long): String {
    val gap = System.currentTimeMillis() - at
    val minutes = gap / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m"
        minutes < 60 * 24 -> "${minutes / 60}h"
        minutes < 60 * 48 -> "yesterday"
        else -> "${minutes / (60 * 24)}d"
    }
}


/**
 * The people on your aux, and the way to add one.
 *
 * Handles rather than a directory: there is nobody to browse and nothing to discover here, which
 * is the point. You add someone because you already know them.
 */
@Composable
private fun People(
    circle: Circle,
    only: String?,
    onPick: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRespond: (Person, Boolean) -> Unit,
) {
    val glass = LocalGlass.current
    var handle by remember { mutableStateOf("") }

    GlassCard(padding = Space.medium) {
        Text("your people", style = Type.section, color = glass.t1)
        Spacer(Modifier.height(Space.small))

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = handle,
                onValueChange = { handle = it.trimStart().take(30) },
                singleLine = true,
                textStyle = Type.rowTitle.copy(color = glass.t1),
                cursorBrush = SolidColor(glass.accent),
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (handle.isNotBlank()) {
                        onAdd(handle.trim())
                        handle = ""
                    }
                }),
                modifier = Modifier
                    .weight(1f)
                    .clip(Shapes.chip)
                    .background(glass.p1)
                    .border(1.dp, glass.bd, Shapes.chip)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                decorationBox = { field ->
                    if (handle.isEmpty()) {
                        Text("add by handle", style = Type.rowTitle, color = glass.t3)
                    }
                    field()
                },
            )
            Spacer(Modifier.width(Space.tight))
            GlassButton(
                label = "add",
                filled = true,
                compact = true,
                enabled = handle.isNotBlank(),
                onClick = {
                    onAdd(handle.trim())
                    handle = ""
                },
            )
        }

        for (person in circle.incoming) {
            Spacer(Modifier.height(Space.small))
            RowRule()
            Row(
                Modifier.fillMaxWidth().padding(top = Space.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("@" + person.handle, style = Type.rowTitle, color = glass.t1)
                    Text("wants on your aux", style = Type.meta, color = glass.t3)
                }
                GlassButton(label = "let in", filled = true, compact = true) { onRespond(person, true) }
                Spacer(Modifier.width(Space.tight))
                GlassButton(label = "no", compact = true) { onRespond(person, false) }
            }
        }

        for (person in circle.outgoing) {
            Spacer(Modifier.height(Space.small))
            RowRule()
            Row(
                Modifier.fillMaxWidth().padding(top = Space.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("@" + person.handle, style = Type.rowTitle, color = glass.t1, modifier = Modifier.weight(1f))
                Text("asked", style = Type.metaStrong, color = glass.t3)
            }
        }

        if (circle.accepted.isNotEmpty()) {
            Spacer(Modifier.height(Space.small))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.tight),
            ) {
                for (person in circle.accepted) {
                    val picked = only == person.handle
                    // a handle is also the way to hear only them
                    Box(
                        Modifier
                            .clip(Shapes.chip)
                            .background(if (picked) glass.sage else glass.sageTint)
                            .border(1.dp, glass.sageBorder, Shapes.chip)
                            .clickable { onPick(person.handle) }
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    ) {
                        Text(
                            "@" + person.handle,
                            style = Type.chip,
                            color = if (picked) glass.onSage else glass.sage,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
