package dev.crossfeed.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import dev.crossfeed.core.Artwork
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import dev.crossfeed.core.Router
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.history.Dashboard
import dev.crossfeed.core.history.Days
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.Play
import dev.crossfeed.core.history.Range
import dev.crossfeed.core.history.Stats
import dev.crossfeed.core.history.Tally
import dev.crossfeed.core.lyrics.LyricsSource
import dev.crossfeed.ui.theme.Accents
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Look
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The diary.
 *
 * One figure, big enough to be the point of the page, then the smaller ones that qualify it, then
 * what is playing, then what has already played today. Everything else the app can do lives
 * somewhere else; this page answers one question and answers it first.
 */
@Composable
fun ListeningScreen() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val prefs = remember { Prefs(context) }

    var range by remember { mutableStateOf(Range.WEEK) }
    val data = Diary.showing
    val week = Diary.week
    var grid by remember { mutableStateOf(prefs.diaryGrid) }
    var showLyrics by remember { mutableStateOf(false) }
    var showCurate by remember { mutableStateOf(false) }
    var forgetting by remember { mutableStateOf<Play?>(null) }
    // folded to start: the five most recent are the ones you want, and the charts
    // underneath are what the page is for
    var open by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // once a day, fold anything reported twice into the one listen it was, then recount. doing
    // it here rather than on every write keeps the capture path cheap
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val since = System.currentTimeMillis() - prefs.tidiedAt
            if (since > 24 * 3600_000L) {
                runCatching { HistoryDb.get(context).tidy() }
                prefs.tidiedAt = System.currentTimeMillis()
                Diary.forget()
            }
        }
    }

    // the diary is about what is happening now, so it keeps up while you are looking at it
    LaunchedEffect(range) {
        while (true) {
            Diary.load(context, range) { withContext(Dispatchers.IO) { spark(context, range) } }
            delay(20_000)
        }
    }

    val deck = rememberDeck()

    val plays = data?.feed.orEmpty()

    LazyVerticalGrid(
        columns = GridCells.Fixed(if (grid && open) 3 else 1),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = Space.large,
            end = Space.large,
            top = Space.small,
            bottom = bottomRoom(),
        ),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalArrangement = Arrangement.spacedBy(if (grid) 9.dp else 0.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                UpdateBanner()
                Spacer(Modifier.height(Space.tight))
                Header()
                Spacer(Modifier.height(Space.medium))
                Periods(range) { range = it }
                Spacer(Modifier.height(Space.small))
                StatCard(data, range, week)
                Spacer(Modifier.height(Space.small))
                PausedCard()
                deck?.let {
                    NowPlayingCard(it)
                    Spacer(Modifier.height(Space.tight))
                    LyricStrip(it) { showLyrics = true }
                    Spacer(Modifier.height(Space.small))
                }
                MoodCard(deck)
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.tight, bottom = Space.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // one diary, folded to five, opening into sections. two cards asking almost
                    // the same question was one card too many
                    Row(
                        Modifier
                            .weight(1f)
                            .clickable { open = !open }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("the diary", style = Type.section, color = glass.t1)
                        Spacer(Modifier.width(Space.tight))
                        Text(
                            if (open) "\u2013" else "+",
                            style = Type.section,
                            color = glass.t3,
                        )
                        plays.size.takeIf { it > FOLDED && !open }?.let {
                            Spacer(Modifier.width(Space.tight))
                            Text("$it", style = Type.stamp, color = glass.t3)
                        }
                    }
                    if (open) {
                        ViewToggle(grid) {
                            grid = it
                            prefs.diaryGrid = it
                        }
                    }
                }
            }
        }

        if (plays.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "nothing yet in this stretch.",
                    style = Type.note,
                    color = glass.t3,
                    modifier = Modifier.padding(vertical = Space.medium),
                )
            }
        }

        if (!open) {
            // folded, it is the last few, because a section that collapses to nothing is one you
            // forget is there
            items(plays.take(FOLDED), key = { it.id }) { play ->
                PlayRow(
                    play,
                    { scope.launch { Router.play(context, play.title, play.artist) }; Unit },
                    { forgetting = play },
                )
            }
            if (plays.size > FOLDED) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "and ${plays.size - FOLDED} more",
                        style = Type.note,
                        color = glass.t3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { open = true }
                            .padding(vertical = Space.small),
                    )
                }
            }
        } else {
            for (stretch in Stretch.entries) {
                val batch = plays.filter { stretch.holds(it.startedAt) }
                if (batch.isEmpty()) continue
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        stretch.label,
                        style = Type.tag,
                        color = glass.t3,
                        modifier = Modifier.padding(top = Space.medium, bottom = Space.tight),
                    )
                }
                items(batch, key = { it.id }) { play ->
                    val onOpen = { scope.launch { Router.play(context, play.title, play.artist) }; Unit }
                    val onHold = { forgetting = play }
                    if (grid) PlayTile(play, onOpen, onHold) else PlayRow(play, onOpen, onHold)
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Spacer(Modifier.height(Space.tight))
                CapsuleCard()
                Spacer(Modifier.height(Space.tight))
                Chart("top artists", data?.artists.orEmpty())
                Chart("most played", data?.tracks.orEmpty())
                Chart("top albums", data?.albums.orEmpty())
                Chart("genres", data?.genres.orEmpty())
                Chart("where you listened", data?.sources.orEmpty()) { sourceLabel(context, it) }
                data?.let { Habits(it) }
                WeatherCard()
                Spacer(Modifier.height(Space.small))
                GlassCard(padding = Space.medium) {
                    Text("a list, made for you", style = Type.section, color = glass.t1)
                    Text(
                        "built from what you already play, on this phone, out of your own files.",
                        style = Type.note,
                        color = glass.t3,
                        modifier = Modifier.padding(top = 4.dp, bottom = Space.small),
                    )
                    GlassButton(label = "make me a list", filled = true, compact = true) {
                        showCurate = true
                    }
                }
            }
        }
    }

    forgetting?.let { play ->
        ForgetTrack(
            play = play,
            onKeep = { forgetting = null },
            onForget = {
                forgetting = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        HistoryDb.get(context).forgetTrack(play.title, play.artist)
                    }
                    Diary.forget()
                    Diary.load(context, range) { withContext(Dispatchers.IO) { spark(context, range) } }
                }
            },
        )
    }

    if (showLyrics) LyricsScreen(onClose = { showLyrics = false })
    if (showCurate) CurateScreen(onClose = { showCurate = false })
}

/**
 * A bar per thing, longest first. The figure is time rather than plays, because an hour of one
 * artist says more than forty seconds of another forty times.
 */
@Composable
private fun Chart(
    title: String,
    rows: List<Tally>,
    label: (String) -> String = { it },
) {
    if (rows.isEmpty()) return
    val glass = LocalGlass.current
    val peak = rows.maxOf { it.listenedMs }.coerceAtLeast(1)

    Spacer(Modifier.height(Space.small))
    GlassCard(padding = Space.medium) {
        Text(title, style = Type.section, color = glass.t1)
        Spacer(Modifier.height(Space.small))
        for (row in rows) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label(row.label),
                    style = Type.rowTitle,
                    color = glass.t1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(Stats.minutes(row.listenedMs), style = Type.stamp, color = glass.t3)
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(glass.p2),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(row.listenedMs.toFloat() / peak)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(glass.accent),
                )
            }
            Spacer(Modifier.height(Space.small))
        }
    }
}

/** The shape of the habit rather than its size. */
@Composable
private fun Habits(data: Dashboard) {
    val glass = LocalGlass.current
    Spacer(Modifier.height(Space.small))
    GlassCard(padding = Space.medium) {
        Text("habits", style = Type.section, color = glass.t1)
        Spacer(Modifier.height(Space.small))
        Row(Modifier.fillMaxWidth()) {
            Figure("day streak", data.habits.streak, Modifier.weight(1f), rule = false)
            Figure("finished", (data.habits.completion * 100).toInt(), Modifier.weight(1f), rule = true)
            Figure("skipped", data.habits.skips, Modifier.weight(1f), rule = true)
        }
        data.habits.peakHour?.let {
            Spacer(Modifier.height(Space.small))
            Text(
                "you listen most around " + Stats.hourLabel(it),
                style = Type.note,
                color = glass.t3,
            )
        }
    }
}

@Composable
private fun Header() {
    val glass = LocalGlass.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("listening", style = Type.page, color = glass.t1, modifier = Modifier.weight(1f))
        ServicePill()
    }
}

/** Which service the app is wearing today, said quietly, in that service's own colour. */
@Composable
private fun ServicePill() {
    val glass = LocalGlass.current
    Row(
        Modifier
            .clip(Shapes.chip)
            .background(glass.p2)
            .border(1.dp, glass.bd, Shapes.chip)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(glass.accent))
        // the same source the colour comes from: whatever is playing, else the lead route
        val context = LocalContext.current
        val playing = dev.crossfeed.core.history.NowPlaying.current?.takeIf { it.playing }?.source
        Text(
            Accents.nameOfApp(context, playing) ?: Accents.nameOf(Look.lead),
            style = Type.metaStrong,
            color = glass.t2,
            maxLines = 1,
        )
    }
}

@Composable
private fun Periods(range: Range, onPick: (Range) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
        for (option in Range.entries) {
            GlassChip(option.label, selected = option == range) { onPick(option) }
        }
    }
}

/**
 * One number set flush left like a poster, the week beside it as bars, and the three counts that
 * qualify it underneath.
 */
@Composable
private fun StatCard(data: Dashboard?, range: Range, week: List<Long>) {
    val glass = LocalGlass.current
    val summary = data?.summary

    GlassCard(padding = Space.medium) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(hoursOf(summary?.listenedMs ?: 0), style = Type.heroFigure, color = glass.t1, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Text(
                    "hours · " + caption(range),
                    style = Type.tag,
                    color = glass.accent,
                    maxLines = 1,
                )
            }
            Sparkline(week)
        }

        Spacer(Modifier.height(Space.medium))
        Box(Modifier.fillMaxWidth().height(1.dp).background(glass.bd))
        Spacer(Modifier.height(Space.medium))

        Row(Modifier.fillMaxWidth()) {
            Figure("plays", summary?.totalPlays ?: 0, Modifier.weight(1f), rule = false)
            Figure("tracks", summary?.distinctTracks ?: 0, Modifier.weight(1f), rule = true)
            Figure("artists", summary?.distinctArtists ?: 0, Modifier.weight(1f), rule = true)
        }
    }
}

@Composable
private fun Figure(label: String, value: Int, modifier: Modifier = Modifier, rule: Boolean) {
    val glass = LocalGlass.current
    Row(modifier) {
        if (rule) {
            Box(Modifier.width(1.dp).height(38.dp).background(glass.bd))
            Spacer(Modifier.width(13.dp))
        }
        Column {
            Text(grouped(value), style = Type.statFigure, color = glass.t1, maxLines = 1)
            Spacer(Modifier.height(3.dp))
            Text(label.uppercase(), style = Type.tagSmall, color = glass.t3, maxLines = 1)
        }
    }
}

/** Seven days as seven bars, with the best one in the accent. */
@Composable
private fun Sparkline(week: List<Long>) {
    val glass = LocalGlass.current
    val peak = week.maxOrNull()?.takeIf { it > 0 } ?: 1L
    Row(
        Modifier.width(104.dp).height(44.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        val bars = week.ifEmpty { List(7) { 0L } }
        for (value in bars) {
            val share = (value.toFloat() / peak.toFloat()).coerceIn(0.06f, 1f)
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp * share)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (value == peak && value > 0) glass.accent else glass.t1.copy(alpha = 0.18f)),
            )
        }
    }
}

@Composable
private fun NowPlayingCard(deck: Deck) {
    val glass = LocalGlass.current
    GlassCard(padding = 13.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Sleeve(deck.title, deck.artwork, 46.dp, Shapes.artSmall)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(deck.title, style = Type.rowTitleLarge, color = glass.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    deck.artist.orEmpty(),
                    style = Type.note,
                    color = glass.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(7.dp))
                Rail(if (deck.durationMs > 0) deck.positionMs.toFloat() / deck.durationMs else 0f)
            }
            Spacer(Modifier.width(11.dp))
            PlayCircle(38.dp, playing = deck.playing) { deck.toggle() }
        }
    }
}

@Composable
private fun Rail(progress: Float) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(glass.line),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(3.dp)
                .clip(CircleShape)
                .background(glass.accent),
        )
    }
}

/** A line of the song, set like a book rather than like an interface. */
@Composable
private fun LyricStrip(deck: Deck, onOpen: () -> Unit) {
    val glass = LocalGlass.current
    val context = LocalContext.current
    var line by remember(deck.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(deck.id) {
        line = runCatching {
            LyricsSource.find(context, deck.title, deck.artist, null, deck.durationMs)
                ?.lines
                ?.firstOrNull { it.text.isNotBlank() }
                ?.text
        }.getOrNull()
    }

    val words = line ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(glass.sageTint)
            .border(1.dp, glass.sageBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            words,
            style = Type.quiet,
            color = glass.t1,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(11.dp))
        Text("sing along", style = Type.tagWide, color = glass.sage, maxLines = 1)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PlayRow(play: Play, onOpen: () -> Unit, onHold: () -> Unit) {
    val glass = LocalGlass.current
    Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = onHold)) {
        RowRule()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Sleeve(play.title, play.artwork, 30.dp, RoundedCornerShape(9.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(play.title, style = Type.rowTitle, color = glass.t1, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    play.artist?.let {
                        Text(
                            " $it",
                            style = Type.meta,
                            color = glass.t3,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // when it was played and how long it was actually listened to, on their own line
                // under the title rather than crowded into it
                Text(
                    listOfNotNull(stampOf(play.startedAt), listenedFor(play.listenedMs))
                        .joinToString(" · "),
                    style = Type.stamp,
                    color = glass.t3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            val done = completion(play)
            Text(
                done,
                style = Type.metaStrong,
                color = if (finished(play)) glass.sage else glass.t3,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier.width(34.dp),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun PlayTile(play: Play, onOpen: () -> Unit, onHold: () -> Unit) {
    val glass = LocalGlass.current
    Column(Modifier.combinedClickable(onClick = onOpen, onLongClick = onHold)) {
        Box {
            Sleeve(play.title, play.artwork, 0.dp, Shapes.artSmall, fill = true)
            Text(
                completion(play),
                style = Type.metaStrong,
                color = if (finished(play)) glass.sage else glass.t2,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .clip(Shapes.chip)
                    .background(glass.scrim)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            play.title,
            style = Type.note,
            color = glass.t1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // a tile is too narrow to carry the date, the time and the length on one line without
        // cutting one of them off, so the length sits under them
        Text(
            stampOf(play.startedAt),
            style = Type.stamp,
            color = glass.t3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        listenedFor(play.listenedMs)?.let {
            Text(it, style = Type.stamp, color = glass.t3, maxLines = 1)
        }
    }
}

/**
 * Album art where there is any, and where there is not, the first letter of the title on a colour
 * mixed from the title itself, so the same record always comes out the same shade.
 */
@Composable
fun Sleeve(
    title: String,
    artwork: String?,
    side: Dp,
    shape: Shape,
    fill: Boolean = false,
) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val base = remember(title) { tintFor(title) }
    var art by remember(artwork) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(artwork) {
        val url = artwork?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        art = runCatching { Artwork.loadUrl(context, url)?.asImageBitmap() }.getOrNull()
        // a row that scrolled into view mid outage should not stay a letter for ever. one more
        // go, a few seconds later, and then it is left alone
        if (art == null) {
            delay(4_000)
            art = runCatching { Artwork.loadUrl(context, url)?.asImageBitmap() }.getOrNull()
        }
    }

    Box(
        (if (fill) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(side))
            .clip(shape)
            .background(Brush.linearGradient(listOf(base, base.copy(alpha = 0.45f).compositeOver(glass.bg)))),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = art
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                title.take(1).uppercase(),
                style = if (fill) Type.sectionLarge else Type.section,
                color = glass.t1.copy(alpha = 0.5f),
            )
        }
    }
}

/** A colour mixed from the name, so a record without a sleeve still has a face. */
private fun tintFor(title: String): Color {
    val hash = title.lowercase().fold(0) { acc, ch -> acc * 31 + ch.code }
    val palette = listOf(
        Color(0xFF5C2A30), Color(0xFF3D472B), Color(0xFF4A2A12),
        Color(0xFF2F3238), Color(0xFF3A2740), Color(0xFF23404A),
    )
    return palette[((hash % palette.size) + palette.size) % palette.size]
}

// figures

/** Hours and minutes, as a clock reads, because that is how long feels. */
private fun hoursOf(ms: Long): String {
    val minutes = ms / 60_000
    return "%d:%02d".format(minutes / 60, minutes % 60)
}

private fun grouped(value: Int): String =
    if (value < 1000) value.toString() else "%,d".format(value).replace(',', ' ')

private fun caption(range: Range): String = when (range) {
    Range.TODAY -> "today"
    Range.WEEK -> "this week"
    Range.MONTH -> "this month"
    Range.ALL -> "all time"
}

private fun completion(play: Play): String {
    if (play.durationMs <= 0) return "·"
    val share = (play.listenedMs * 100 / play.durationMs).toInt()
    return if (share >= 100) "${share / 100}×".takeIf { share >= 200 } ?: "100%" else "$share%"
}

private fun finished(play: Play): Boolean =
    play.durationMs > 0 && play.listenedMs * 100 / play.durationMs >= 95

/** How much of the day stays on screen when the section is folded. */
private const val FOLDED = 5

/**
 * The bars under the hero figure, bucketed to match whatever stretch is showing: hours across a
 * day, days across a week, threes of days across a month, months across everything.
 */
private fun spark(context: android.content.Context, range: Range): List<Long> {
    val db = HistoryDb.get(context)
    return when (range) {
        Range.TODAY -> {
            val byHour = db.hourHistogram(Days.ago(0)).toMap()
            (0 until 8).map { slot -> (0 until 3).sumOf { byHour[slot * 3 + it] ?: 0L } }
        }

        Range.WEEK -> {
            val byDay = db.activeDays(Days.ago(6)).toMap()
            (6 downTo 0).map { back -> byDay[Days.ago(back)] ?: 0L }
        }

        Range.MONTH -> {
            val byDay = db.activeDays(Days.ago(29)).toMap()
            (9 downTo 0).map { block -> (0 until 3).sumOf { byDay[Days.ago(block * 3 + it)] ?: 0L } }
        }

        Range.ALL -> {
            val byMonth = LinkedHashMap<String, Long>()
            for ((day, listened) in db.activeDays()) {
                val month = day.take(7)
                byMonth[month] = (byMonth[month] ?: 0L) + listened
            }
            byMonth.entries.sortedBy { it.key }.takeLast(12).map { it.value }
        }
    }
}

/** Which app a play came from, named the way its own launcher names it. */
private fun sourceLabel(context: android.content.Context, pkg: String): String = runCatching {
    val manager = context.packageManager
    manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString().lowercase()
}.getOrDefault(pkg.substringAfterLast('.'))


/**
 * Some things play without being listened to: a tab left open, a record that carried on after you
 * walked away. They are honestly captured and still wrong, so there is a way to say so, and it
 * takes the track out of the totals rather than only out of the list.
 */
@Composable
private fun ForgetTrack(play: Play, onKeep: () -> Unit, onForget: () -> Unit) {
    val glass = LocalGlass.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onKeep) {
        GlassCard(strong = true, padding = Space.medium) {
            Text("forget this one?", style = Type.section, color = glass.t1)
            Text(
                play.title + (play.artist?.let { " · $it" } ?: ""),
                style = Type.rowTitle,
                color = glass.t2,
                modifier = Modifier.padding(top = Space.tight),
            )
            Text(
                "every play of it goes, and so does its share of your hours, your plays and every " +
                    "chart. nothing else is touched.",
                style = Type.note,
                color = glass.t3,
                modifier = Modifier.padding(top = 4.dp, bottom = Space.small),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                GlassButton(
                    label = "forget it",
                    filled = true,
                    modifier = Modifier.weight(1f),
                    onClick = onForget,
                )
                GlassButton(label = "keep it", modifier = Modifier.weight(1f), onClick = onKeep)
            }
        }
    }
}


/**
 * How the diary is cut up when it is open.
 *
 * Not by date, by how recent it feels: what has happened today, the couple of days you can still
 * remember, the week behind that, and everything before it.
 */
private enum class Stretch(val label: String, private val fromDays: Int, private val toDays: Int) {
    TODAY("TODAY", 0, 0),
    RECENT("THE LAST TWO DAYS", 1, 2),
    WEEK("THIS PAST WEEK", 3, 7),
    OLDER("BEFORE THAT", 8, Int.MAX_VALUE);

    fun holds(startedAt: Long): Boolean {
        val days = daysAgo(startedAt)
        return days in fromDays..toDays
    }

    private fun daysAgo(startedAt: Long): Int {
        val start = java.util.Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (startedAt >= start) return 0
        return (((start - startedAt) / 86_400_000L) + 1).toInt()
    }
}
