package dev.crossfeed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.crossfeed.core.catalog.Catalog
import dev.crossfeed.core.net.Social
import dev.crossfeed.core.player.Sources
import dev.crossfeed.core.player.Track
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What is being handed over: something already known, or something searched for. */
private data class Choice(
    val title: String,
    val artist: String?,
    val album: String?,
    val art: String?,
    val link: String?,
    val here: Boolean,
)

/**
 * Sending somebody a song.
 *
 * This is the one thing in crossfeed addressed to a person rather than posted at everybody, so it
 * asks two questions in one place: what, and to whom. What can come from this phone or from any
 * shop the app knows; who can only be someone already on your aux.
 */
@Composable
fun SendSong(
    to: String,
    replyTo: String? = null,
    onSent: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Choice>>(emptyList()) }
    var looking by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        looking = true
        delay(240)
        // what is on this phone first, then what the shops know, since the thing you want to hand
        // over is usually the thing you were just playing
        val mine = withContext(Dispatchers.IO) {
            runCatching { Sources.search(context, query, limit = 8) }.getOrDefault(emptyList())
        }.map { it.toChoice() }
        val known = mine.map { it.title.lowercase() + "|" + it.artist.orEmpty().lowercase() }.toSet()
        val out = withContext(Dispatchers.IO) {
            runCatching { Catalog.search(context, query, limit = 12) }.getOrDefault(emptyList())
        }
            .filterNot { (it.title.lowercase() + "|" + it.artist.orEmpty().lowercase()) in known }
            .map {
                Choice(
                    title = it.title,
                    artist = it.artist,
                    album = it.album,
                    art = it.artwork,
                    link = it.url,
                    here = false,
                )
            }
        results = mine + out
        looking = false
    }

    fun hand(choice: Choice) {
        if (sending) return
        sending = true
        scope.launch {
            runCatching {
                Social.send(
                    context = context,
                    to = to,
                    title = choice.title,
                    artist = choice.artist,
                    album = choice.album,
                    art = choice.art,
                    link = choice.link,
                    postId = replyTo,
                )
            }
                .onSuccess { onSent("sent " + choice.title + " to @" + to.removePrefix("@")) }
                .onFailure { onSent(it.message ?: "could not send that") }
            sending = false
            onDismiss()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassCard(strong = true, padding = Space.medium) {
            Text(
                if (replyTo != null) "answer with a song" else "send a song",
                style = Type.section,
                color = glass.t1,
            )
            Text(
                "to @" + to.removePrefix("@") + ", and to nobody else.",
                style = Type.note,
                color = glass.t3,
                modifier = Modifier.padding(top = 4.dp, bottom = Space.small),
            )

            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = Type.rowTitle.copy(color = glass.t1),
                cursorBrush = SolidColor(glass.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Shapes.chip)
                    .background(glass.p1)
                    .border(1.dp, glass.bd, Shapes.chip)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                decorationBox = { field ->
                    if (query.isEmpty()) {
                        Text("what are you sending?", style = Type.rowTitle, color = glass.t3)
                    }
                    field()
                },
            )

            Spacer(Modifier.height(Space.small))
            if (query.isNotBlank() && results.isEmpty()) {
                Text(
                    if (looking) "looking…" else "nothing by that name.",
                    style = Type.note,
                    color = glass.t3,
                )
            }

            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(results, key = { it.title + "|" + it.artist.orEmpty() + "|" + it.here }) { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { hand(choice) }
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Sleeve(choice.title, choice.art, 34.dp, Shapes.artRow)
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                choice.title,
                                style = Type.rowTitle,
                                color = glass.t1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                choice.artist.orEmpty(),
                                style = Type.meta,
                                color = glass.t3,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (choice.here) {
                            Box(
                                Modifier
                                    .clip(Shapes.chip)
                                    .background(glass.sageTint)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text("here", style = Type.metaStrong, color = glass.sage)
                            }
                        }
                    }
                    RowRule()
                }
            }

            Spacer(Modifier.height(Space.small))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                GlassButton(label = "not now", modifier = Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

private fun Track.toChoice() = Choice(
    title = title,
    artist = artist,
    album = album,
    art = artwork,
    link = null,
    here = true,
)

/** Everyone you could hand a song to, which is everyone already on your aux. */
@Composable
fun PickPerson(handles: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val glass = LocalGlass.current
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(strong = true, padding = Space.medium) {
            Text("send to", style = Type.section, color = glass.t1)
            if (handles.isEmpty()) {
                Text(
                    "nobody on your aux yet. add someone by handle first.",
                    style = Type.note,
                    color = glass.t3,
                    modifier = Modifier.padding(top = Space.tight),
                )
            }
            Spacer(Modifier.height(Space.small))
            for (handle in handles) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(handle) }
                        .padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("@$handle", style = Type.rowTitle, color = glass.t1, modifier = Modifier.weight(1f))
                }
                RowRule()
            }
            Spacer(Modifier.height(Space.small))
            GlassButton(label = "not now", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

/** Everyone already on your aux, which is everyone a song can be handed to. */
@Composable
fun rememberHandles(): List<String> = Aux.circle.accepted.map { it.handle }
