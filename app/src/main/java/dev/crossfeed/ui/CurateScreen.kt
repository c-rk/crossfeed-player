package dev.crossfeed.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.crossfeed.core.Router
import dev.crossfeed.core.curate.Curator
import dev.crossfeed.core.curate.Pick
import dev.crossfeed.core.curate.Recipe
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.Stats
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurateScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val db = remember { HistoryDb.get(context) }
    val genres = remember {
        val mine = db.knownGenres(40).mapNotNull { dev.crossfeed.core.curate.Genre.of(it) }.distinct()
        mine + dev.crossfeed.core.curate.Genre.canonical.filterNot { it in mine }
    }

    var minutes by remember { mutableIntStateOf(45) }
    var familiarity by remember { mutableStateOf(0.5f) }
    var chosen by remember { mutableStateOf(emptySet<String>()) }
    var languages by remember { mutableStateOf(emptySet<String>()) }
    var albums by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<dev.crossfeed.core.curate.Result?>(null) }

    fun run() {
        working = true
        scope.launch {
            result = Curator.build(
                context,
                Recipe(
                    minutes = minutes,
                    familiarity = familiarity,
                    genres = chosen,
                    languages = languages,
                    preferAlbums = albums,
                ),
            )
            working = false
        }
    }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(glass.wash))) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = Space.large),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("curation", style = Type.wordmark, color = glass.ink)
                        Text(
                            "a list to play wherever you like",
                            style = Type.footnote,
                            color = glass.inkMuted,
                        )
                    }
                    GlassButton(label = "close", compact = true, onClick = onClose)
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
                ) {
                    item {
                        Spacer(Modifier.height(Space.small))
                        GlassCard {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("how long", style = Type.body, color = glass.inkMuted, modifier = Modifier.weight(1f))
                                Text("$minutes min", style = Type.headline, color = glass.ink)
                            }
                            Slider(
                                value = minutes.toFloat(),
                                onValueChange = { minutes = it.toInt() },
                                valueRange = 10f..180f,
                                colors = SliderDefaults.colors(
                                    thumbColor = glass.accent,
                                    activeTrackColor = glass.accent,
                                ),
                            )

                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("familiarity", style = Type.body, color = glass.inkMuted, modifier = Modifier.weight(1f))
                                Text(
                                    when {
                                        familiarity <= 0.02f -> "completely new"
                                        familiarity < 0.25f -> "mostly new"
                                        familiarity < 0.45f -> "lean new"
                                        familiarity < 0.56f -> "an even mix"
                                        familiarity < 0.78f -> "lean familiar"
                                        familiarity < 0.98f -> "mostly familiar"
                                        else -> "very familiar"
                                    },
                                    style = Type.callout,
                                    color = glass.ink,
                                )
                            }
                            Slider(
                                value = familiarity,
                                onValueChange = { familiarity = it },
                                colors = SliderDefaults.colors(
                                    thumbColor = glass.accent,
                                    activeTrackColor = glass.accent,
                                ),
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("completely new", style = Type.caps, color = glass.inkFaint)
                                Text(
                                    "${(familiarity * 100).toInt()}% you know",
                                    style = Type.caps,
                                    color = glass.inkFaint,
                                )
                                Text("very familiar", style = Type.caps, color = glass.inkFaint)
                            }

                            ToggleRow(
                                title = "prefer albums",
                                subtitle = "fit whole albums into the time instead of loose tracks",
                                checked = albums,
                                onChange = { albums = it },
                            )
                        }
                    }

                    if (genres.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(Space.medium))
                            GlassCard {
                                SectionHeader("genres") {
                                    if (chosen.isNotEmpty()) {
                                        Text("${chosen.size}", style = Type.footnote, color = glass.inkFaint)
                                    }
                                }
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(Space.tight),
                                    verticalArrangement = Arrangement.spacedBy(Space.tight),
                                ) {
                                    for (genre in genres) {
                                        GlassChip(
                                            label = genre,
                                            selected = genre in chosen,
                                            onClick = {
                                                chosen = if (genre in chosen) chosen - genre else chosen + genre
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(Space.medium))
                        GlassCard {
                            SectionHeader("language") {
                                Text(
                                    if (languages.isEmpty()) "any" else "${languages.size}",
                                    style = Type.footnote,
                                    color = glass.inkFaint,
                                )
                            }
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(Space.tight),
                                verticalArrangement = Arrangement.spacedBy(Space.tight),
                            ) {
                                for (option in dev.crossfeed.core.curate.Language.options) {
                                    GlassChip(
                                        label = option,
                                        selected = option in languages,
                                        onClick = {
                                            languages = if (option in languages) {
                                                languages - option
                                            } else {
                                                languages + option
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Spacer(Modifier.height(Space.medium))
                        GlassButton(
                            label = if (working) "putting it together…" else "make me a list",
                            filled = true,
                            enabled = !working,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { run() },
                        )
                    }

                    result?.let { made ->
                        item {
                            Spacer(Modifier.height(Space.medium))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${made.picks.size} tracks · ${Stats.minutes(made.totalMs)}" +
                                        if (albums) " · ${made.albums} albums" else "",
                                    style = Type.callout,
                                    color = glass.ink,
                                    modifier = Modifier.weight(1f),
                                )
                                GlassButton(
                                    label = "share",
                                    compact = true,
                                    onClick = { share(context, made.picks) },
                                )
                            }
                            Text(
                                if (made.short) {
                                    "could not reach $minutes min with these filters. loosen the " +
                                        "language or genre and try again."
                                } else {
                                    "tap any track to open it in your player"
                                },
                                style = Type.footnote,
                                color = if (made.short) glass.warning else glass.inkFaint,
                                modifier = Modifier.padding(vertical = Space.tight),
                            )
                        }
                        items(made.picks) { pick ->
                            PickRow(pick) {
                                scope.launch { Router.play(context, pick.title, pick.artist) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickRow(pick: Pick, onOpen: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = Space.tight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UrlArt(pick.artwork, pick.title, 44.dp)
        Column(Modifier.weight(1f).padding(start = Space.small)) {
            Text(
                pick.title,
                style = Type.callout,
                color = glass.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(pick.artist.takeIf { it.isNotBlank() }, pick.album).joinToString(" · "),
                style = Type.footnote,
                color = glass.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            (if (pick.known) "known · " else "") + Stats.clock(pick.durationMs),
            style = Type.footnote,
            color = if (pick.known) glass.accent else glass.inkFaint,
        )
    }
}

private fun share(context: android.content.Context, picks: List<Pick>) {
    val text = picks.joinToString("\n") { "${it.title} — ${it.artist}" }
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
            "share this list",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

