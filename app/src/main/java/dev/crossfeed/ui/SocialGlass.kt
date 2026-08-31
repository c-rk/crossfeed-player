package dev.crossfeed.ui

import androidx.compose.foundation.verticalScroll
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
import dev.crossfeed.core.Prefs
import androidx.compose.foundation.horizontalScroll
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
 * and each one does a different thing: two are opinions, one is an action, and the last hands back
 * a song rather than a face. That last one is sage, because what it produces comes from a person.
 */
private data class Reaction(val emoji: String, val label: String, val sage: Boolean = false)

private val reactions = listOf(
    Reaction("🔥", "a banger"),
    Reaction("🎧", "on it now"),
    Reaction("💾", "keeping it"),
    Reaction("↩", "reply", sage = true),
)

/**
 * The aux.
 *
 * Everything on this page belongs to someone else, which is why the light behind it is sage and
 * why the notifications never take you anywhere: the tray opens in place, says its piece, and
 * closes again.
 */
@Composable
fun SocialScreen() {
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
            SocialLegacy()
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

    // while the page is open it keeps itself current, so a friend accepting, a song starting
    // somewhere else, or a reaction arriving turns up on its own rather than on a swipe
    LaunchedEffect(Unit) {
        while (true) {
            Aux.refresh(context)
            delay(6_000)
        }
    }

    fun react(post: Post, reaction: Reaction) {
        if (reaction.label == "reply") {
            note = "song replies need the other half of this, which is not live yet"
            picking = null
            return
        }
        val next = if (post.mine == reaction.emoji) null else reaction.emoji
        Aux.replace(posts.map { if (it.id == post.id) it.withReaction(next) else it })
        picking = null
        scope.launch {
            runCatching { Social.react(context, post.id, next.orEmpty()) }
            // keeping it does what it says as well as saying it
            if (reaction.label == "keeping it" && next != null) {
                runCatching { Social.save(context, post) }
                note = "kept ${post.title}"
            }
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid) 2 else 1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Space.large, end = Space.large, top = Space.small, bottom = bottomRoom()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(if (grid) 10.dp else 0.dp),
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
                            scope.launch { runCatching { Social.markAlertsSeen(context) } }
                        }
                    }
                }

                Spacer(Modifier.height(Space.small))
                Rise(trayOpen) { Tray(alerts) { trayOpen = false } }
                if (trayOpen) Spacer(Modifier.height(Space.small))

                if (live.isNotEmpty()) {
                    Listening(live)
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

                Aux.trouble?.let {
                    Text(
                        it,
                        style = Type.note,
                        color = glass.warning,
                        modifier = Modifier.padding(bottom = Space.tight),
                    )
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
                    Text(today(posts), style = Type.stamp, color = glass.t3, modifier = Modifier.weight(1f))
                    ViewToggle(grid) {
                        grid = it
                        prefs.auxGrid = it
                    }
                }
            }
        }

        if (posts.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    when {
                        Aux.trouble != null -> Aux.trouble.orEmpty()
                        Aux.loadedAt == 0L -> "reading the aux\u2026"
                        else -> "nothing on the aux yet."
                    },
                    style = Type.note,
                    color = if (Aux.trouble != null) glass.warning else glass.t3,
                )
            }
        }

        items(posts, key = { it.id }) { post ->
            if (grid) {
                FeedTile(post) { picking = post.id }
            } else {
                FeedRow(post) { picking = post.id }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            val target = posts.firstOrNull { it.id == picking }
            Rise(target != null) {
                Column {
                    Spacer(Modifier.height(Space.small))
                    Picker(
                        onPick = { reaction -> target?.let { react(it, reaction) } },
                        onDismiss = { picking = null },
                    )
                }
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
private fun Listening(live: List<Live>) {
    val glass = LocalGlass.current
    Row(horizontalArrangement = Arrangement.spacedBy(Space.medium)) {
        for (person in live.take(3)) {
            Column(
                Modifier.width(70.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(glass.sage.copy(alpha = 0.35f))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
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
                Text("@" + person.handle, style = Type.metaStrong, color = glass.t1, maxLines = 1)
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

@Composable
private fun FeedRow(post: Post, onHold: () -> Unit) {
    val glass = LocalGlass.current
    Column(Modifier.clickable(onClick = onHold)) {
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
            }
            Spacer(Modifier.width(Space.tight))
            Bubbles(post)
        }
    }
}

@Composable
private fun FeedTile(post: Post, onHold: () -> Unit) {
    val glass = LocalGlass.current
    GlassCard(shape = Shapes.grid, padding = 10.dp, modifier = Modifier.clickable(onClick = onHold)) {
        Sleeve(post.title, post.art, 0.dp, Shapes.artRow, fill = true)
        Spacer(Modifier.height(7.dp))
        Text(post.title, style = Type.label, color = glass.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            "@" + post.handle + " · " + age(post.updatedAt),
            style = Type.meta,
            color = glass.t3,
            maxLines = 1,
        )
        Spacer(Modifier.height(7.dp))
        Bubbles(post)
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

/** The four, thumb sized, in the one order they ever appear in. */
@Composable
private fun Picker(onPick: (Reaction) -> Unit, onDismiss: () -> Unit) {
    val glass = LocalGlass.current
    GlassCard(padding = 15.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("hold to react", style = Type.sectionSmall, color = glass.t1, modifier = Modifier.weight(1f))
            Text(
                "CLOSE",
                style = Type.tagWide,
                color = glass.t3,
                modifier = Modifier.clip(Shapes.chip).clickable(onClick = onDismiss).padding(4.dp),
            )
        }
        Spacer(Modifier.height(Space.medium))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (reaction in reactions) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    reaction.sage -> glass.sage
                                    reaction == reactions.first() -> glass.accent
                                    else -> glass.p2
                                },
                            )
                            .border(
                                1.dp,
                                if (reaction.sage || reaction == reactions.first()) Color.Transparent else glass.bd,
                                CircleShape,
                            )
                            .clickable { onPick(reaction) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            reaction.emoji,
                            style = Type.sectionSmall,
                            color = if (reaction.sage) glass.onSage else glass.t1,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(reaction.label, style = Type.metaStrong, color = glass.t3, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(Space.small))
        Text(
            "four, thumb sized, always the same four. the reply sends a song back instead of a face.",
            style = Type.note,
            color = glass.t3,
        )
    }
}

private fun people(count: Int): String = when (count) {
    0 -> "nobody yet, no audience"
    1 -> "one person, no audience"
    else -> "$count people, no audience"
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
                    Box(
                        Modifier
                            .clip(Shapes.chip)
                            .background(glass.sageTint)
                            .border(1.dp, glass.sageBorder, Shapes.chip)
                            .padding(horizontal = 11.dp, vertical = 6.dp),
                    ) {
                        Text("@" + person.handle, style = Type.chip, color = glass.sage, maxLines = 1)
                    }
                }
            }
        }
    }
}
