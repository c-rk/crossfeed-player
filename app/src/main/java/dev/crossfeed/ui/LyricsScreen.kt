package dev.crossfeed.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.crossfeed.core.history.NowPlaying
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.lyrics.Lyrics
import dev.crossfeed.core.lyrics.Meaning
import dev.crossfeed.core.lyrics.LyricsSource
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.delay

@Composable
fun LyricsScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val playing = NowPlaying.current

    var lyrics by remember(playing?.key) { mutableStateOf<Lyrics?>(null) }
    var looking by remember(playing?.key) { mutableStateOf(true) }
    var romanised by remember { mutableStateOf(true) }
    var meaning by remember { mutableStateOf(false) }
    var meanings by remember(playing?.key) { mutableStateOf<List<String?>?>(null) }
    var translating by remember { mutableStateOf(false) }
    val offersMeaning = remember { Prefs(context).translateLyrics }
    var position by remember { mutableStateOf(0L) }

    LaunchedEffect(playing?.key) {
        val track = playing ?: return@LaunchedEffect
        looking = true
        lyrics = LyricsSource.find(context, track.title, track.artist, track.album, track.durationMs)
        looking = false
    }

    LaunchedEffect(playing?.key, lyrics) {
        while (true) {
            position = NowPlaying.current?.positionNow() ?: 0L
            delay(220)
        }
    }

    LaunchedEffect(meaning, lyrics, playing?.key) {
        val words = lyrics
        if (!meaning || words == null || words.lines.isEmpty()) return@LaunchedEffect
        if (meanings != null) return@LaunchedEffect
        translating = true
        meanings = Meaning.forLines(
            songKey = playing?.key.orEmpty(),
            lines = words.lines.map { it.text },
            target = Meaning.deviceLanguage(),
        )
        translating = false
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
                        Text("sing along", style = Type.wordmark, color = glass.ink, maxLines = 1)
                        Text(
                            playing?.let { "${it.title} · ${it.artist ?: "unknown artist"}" } ?: "nothing playing",
                            style = Type.footnote,
                            color = glass.inkMuted,
                            maxLines = 1,
                        )
                    }
                    GlassButton(label = "close", compact = true, onClick = onClose)
                }

                Spacer(Modifier.height(Space.small))

                val words = lyrics
                when {
                    playing == null -> Hint("play something and the words will follow along here.")
                    looking -> Hint("looking for the words…")
                    words == null -> Hint("no lyrics found for this one.")
                    words.instrumental -> Hint("this one is instrumental.")
                    words.empty -> Hint("no lyrics found for this one.")
                    else -> {
                        Row(
                            Modifier.padding(bottom = Space.small),
                            horizontalArrangement = Arrangement.spacedBy(Space.tight),
                        ) {
                            if (words.lines.any { LyricsSource.romanisable(it.text) }) {
                                Toggle("original", !romanised) { romanised = false }
                                Toggle("romanised", romanised) { romanised = true }
                            }
                            if (offersMeaning) {
                                Toggle(
                                    if (translating) "working it out…" else "what does it mean?",
                                    meaning,
                                ) { meaning = !meaning }
                            }
                        }
                        Words(
                            words,
                            position,
                            romanised,
                            if (meaning) meanings else null,
                            Modifier.weight(1f),
                        )
                        Text(
                            if (words.synced) "in time with what you are playing" else "not time synced",
                            style = Type.caps,
                            color = glass.inkFaint,
                            modifier = Modifier.fillMaxWidth().padding(vertical = Space.small),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    val glass = LocalGlass.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = Type.body, color = glass.inkMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .clickable(onClick = onClick)
            .background(
                if (on) glass.accent.copy(alpha = 0.28f) else glass.fill,
                androidx.compose.foundation.shape.CircleShape,
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(label, style = Type.footnote, color = if (on) glass.ink else glass.inkMuted)
    }
}

@Composable
private fun Words(
    words: Lyrics,
    positionMs: Long,
    romanised: Boolean,
    meanings: List<String?>?,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    val listState = rememberLazyListState()
    val active = words.indexAt(positionMs)
    var lastScrolled by remember { mutableIntStateOf(-1) }

    val shown = remember(words, romanised) {
        words.lines.map { if (romanised) LyricsSource.romanise(it.text) else it.text }
    }

    LaunchedEffect(active) {
        if (words.synced && active >= 0 && active != lastScrolled) {
            lastScrolled = active
            listState.animateScrollToItem(active.coerceAtLeast(0), -260)
        }
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxWidth()) {
        itemsIndexed(shown) { index, text ->
            val current = words.synced && index == active
            val colour by animateColorAsState(
                targetValue = when {
                    current -> glass.ink
                    !words.synced -> glass.inkMuted
                    index < active -> glass.inkFaint
                    else -> glass.inkMuted
                },
                label = "line",
            )
            val sense = meanings?.getOrNull(index)?.takeIf { it.isNotBlank() && it != text }
            Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Text(
                    text.ifBlank { "·" },
                    style = if (current) {
                        Type.title.copy(fontSize = 30.sp, lineHeight = 37.sp)
                    } else {
                        Type.headline
                    },
                    color = colour,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (sense != null) {
                    Text(
                        sense,
                        style = Type.caps.copy(letterSpacing = 0.sp),
                        color = if (current) glass.accent else glass.inkFaint,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(320.dp)) }
    }
}
