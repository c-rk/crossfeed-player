package dev.crossfeed.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.crossfeed.core.Artwork
import dev.crossfeed.core.Router
import dev.crossfeed.core.export.Export
import dev.crossfeed.core.export.Workbook
import dev.crossfeed.core.history.Dashboard
import dev.crossfeed.core.history.Days
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.ListeningService
import dev.crossfeed.core.history.Play
import dev.crossfeed.core.history.Range
import dev.crossfeed.core.history.Stats
import dev.crossfeed.core.history.Tally
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ListeningScreen() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val db = remember { HistoryDb.get(context) }

    var range by remember { mutableStateOf(Range.WEEK) }
    var query by remember { mutableStateOf("") }
    var dashboard by remember { mutableStateOf<Dashboard?>(null) }
    var capturing by remember { mutableStateOf(ListeningService.enabled(context)) }
    var dbSize by remember { mutableLongStateOf(0L) }
    var feedRows by remember { mutableIntStateOf(0) }
    var reload by remember { mutableStateOf(0) }
    var eraseDays by remember { mutableIntStateOf(30) }
    var confirming by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf<Export?>(null) }
    var exportNote by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var openPlay by remember { mutableStateOf<Long?>(null) }

    suspend fun refresh() {
        dashboard = Stats.load(context, range, query)
    }

    suspend fun measure() {
        withContext(Dispatchers.IO) {
            dbSize = db.sizeBytes(context)
            feedRows = db.feedCount()
        }
    }

    LaunchedEffect(query) {
        if (query.isNotBlank()) delay(280)
        refresh()
    }

    LaunchedEffect(range, reload) { refresh() }

    LaunchedEffect(reload) { measure() }

    LaunchedEffect(Unit) { Stats.enrichGenres(context) }

    LaunchedEffect(capturing, range, query) {
        while (capturing) {
            delay(20_000)
            refresh()
        }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.large),
    ) {
        Spacer(Modifier.height(Space.medium))
        Text("listening", style = Type.wordmark, color = glass.ink)

        if (!capturing) {
            Spacer(Modifier.height(Space.medium))
            GlassCard {
                Text("nothing is being logged yet", style = Type.headline, color = glass.ink)
                Text(
                    "crossfeed reads the now-playing card of any music player on this phone. " +
                        "video apps and browsers are ignored, and nothing leaves the device.",
                    style = Type.footnote,
                    color = glass.inkMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = Space.medium),
                )
                GlassButton(
                    label = "turn on listening history",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
                Spacer(Modifier.height(Space.tight))
                GlassButton(
                    label = "i turned it on",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        ListeningService.rebind(context)
                        capturing = ListeningService.enabled(context)
                        reload++
                    },
                )
            }
        }

        Spacer(Modifier.height(Space.medium))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
            for (option in Range.entries) {
                GlassChip(label = option.label, selected = option == range, onClick = { range = option })
            }
        }

        val data = dashboard
        Spacer(Modifier.height(Space.medium))

        GlassCard {
            SectionHeader("i listened to...")
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                Stat("minutes", Stats.minutes(data?.summary?.listenedMs ?: 0), Modifier.weight(1f))
                Stat("plays", "${data?.summary?.totalPlays ?: 0}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.small))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                Stat("tracks", "${data?.summary?.distinctTracks ?: 0}", Modifier.weight(1f))
                Stat("artists", "${data?.summary?.distinctArtists ?: 0}", Modifier.weight(1f))
            }
        }

        data?.let { d ->
            if (d.summary.totalPlays > 0) {
                Spacer(Modifier.height(Space.medium))
                GlassCard {
                    SectionHeader("habits")
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                        Stat("day streak", "${d.habits.streak}", Modifier.weight(1f))
                        Stat(
                            "peak hour",
                            d.habits.peakHour?.let { Stats.hourLabel(it) } ?: "none yet",
                            Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(Space.small))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                        Stat("finished", "${(d.habits.completion * 100).toInt()}%", Modifier.weight(1f))
                        Stat("skipped", "${d.habits.skips}", Modifier.weight(1f))
                    }
                }
            }
        }

        Chart("top artists", data?.artists.orEmpty())
        Chart("most played", data?.tracks.orEmpty()) { row ->
            scope.launch { Router.play(context, row.label, null) }
        }
        Chart("top albums", data?.albums.orEmpty())
        Chart("genres", data?.genres.orEmpty())
        Chart(
            title = "where you listened",
            rows = data?.sources.orEmpty(),
            icons = true,
            label = { appLabel(context, it) },
        )

        data?.let { d ->
            Spacer(Modifier.height(Space.medium))
            GlassCard {
                SectionHeader("advanced stats") {
                    GlassChip(
                        label = if (showAdvanced) "hide" else "show",
                        selected = showAdvanced,
                        onClick = { showAdvanced = !showAdvanced },
                    )
                }
                if (showAdvanced) {
                    val a = d.advanced
                    Line("longest session", Stats.minutes(a.longestSessionMs))
                    a.mostSkipped?.let { Line("most skipped", "${it.label} · ${it.plays}x") }
                    Line("days with music", "${a.activeDays}")
                    Line("average per day", Stats.minutes(a.perDayMs))
                    a.biggestDay?.let { Line("biggest day", "${it.first} · ${Stats.minutes(it.second)}") }
                    Line("new artists", "${a.newArtists}")
                    Line("new tracks", "${a.newTracks}")
                    a.obsession?.let { Line("on repeat", "${it.first} · ${it.third}x on ${it.second}") }
                    Line("weekdays", Stats.minutes(a.weekdayMs))
                    Line("weekends", Stats.minutes(a.weekendMs))
                    for ((source, rate, skips) in a.bySource) {
                        Line(
                            "${appLabel(context, source)} finish rate",
                            "${(rate * 100).toInt()}% · $skips skipped",
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.medium))
        SearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "search your history",
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Space.medium))
        GlassCard {
            SectionHeader("feed")
            val feed = data?.feed.orEmpty()
            if (feed.isEmpty()) {
                Text(
                    if (capturing) "nothing here yet, play something" else "logging is off",
                    style = Type.body,
                    color = glass.inkMuted,
                    modifier = Modifier.padding(vertical = Space.small),
                )
            } else {
                Text(
                    "double tap to play · tap for play and delete",
                    style = Type.footnote,
                    color = glass.inkFaint,
                    modifier = Modifier.padding(bottom = Space.small),
                )
            }
            for (row in feed.take(FEED_PREVIEW).chunked(3)) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Space.small),
                    horizontalArrangement = Arrangement.spacedBy(Space.tight),
                ) {
                    for (play in row) {
                        Box(Modifier.weight(1f)) {
                            PlayTile(
                                play = play,
                                live = play.id == feed.first().id &&
                                    System.currentTimeMillis() - play.startedAt < 15 * 60_000,
                                stamp = ago(play.startedAt),
                                open = openPlay == play.id,
                                onTap = { openPlay = if (openPlay == play.id) null else play.id },
                                onPlay = { scope.launch { Router.play(context, play.title, play.artist) } },
                                onRemove = {
                                    openPlay = null
                                    scope.launch {
                                        withContext(Dispatchers.IO) { db.delete(play.id) }
                                        reload++
                                    }
                                },
                            )
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (feed.size > FEED_PREVIEW) {
                GlassButton(
                    label = "see all ${feed.size} plays",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showHistory = true },
                )
            }
        }

        Spacer(Modifier.height(Space.medium))
        GlassCard {
            SectionHeader("storage")
            Text(
                "the feed lives in listening.db, private to crossfeed. erasing it frees space. " +
                    "your stats above are kept whatever you delete.",
                style = Type.footnote,
                color = glass.inkMuted,
            )
            Spacer(Modifier.height(Space.small))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("$feedRows rows on disk", style = Type.body, color = glass.inkMuted)
                Text(Stats.bytes(dbSize), style = Type.headline, color = glass.ink)
            }

            Spacer(Modifier.height(Space.medium))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("erase anything older than", style = Type.body, color = glass.inkMuted)
                Spacer(Modifier.weight(1f))
                Text("$eraseDays days", style = Type.headline, color = glass.ink)
            }
            Slider(
                value = eraseDays.toFloat(),
                onValueChange = { eraseDays = it.toInt().coerceAtLeast(1) },
                valueRange = 1f..365f,
                colors = SliderDefaults.colors(
                    thumbColor = glass.accent,
                    activeTrackColor = glass.accent,
                    inactiveTrackColor = glass.fill,
                ),
            )
            GlassButton(label = "erase", compact = true, onClick = { confirming = true })
        }

        Spacer(Modifier.height(Space.medium))
        GlassCard {
            SectionHeader("export")
            Text(
                "every play, every total, every finish rate, written to a spreadsheet you own. " +
                    "eleven sheets, saved to downloads.",
                style = Type.footnote,
                color = glass.inkMuted,
            )
            Spacer(Modifier.height(Space.small))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                GlassButton(
                    label = if (exporting) "writing…" else "export .xlsx",
                    filled = true,
                    enabled = !exporting,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        exporting = true
                        scope.launch {
                            runCatching { Workbook.export(context) }
                                .onSuccess {
                                    exported = it
                                    exportNote = "exported ${it.path} · ${Stats.bytes(it.bytes)}"
                                }
                                .onFailure { exportNote = "could not write the file" }
                            exporting = false
                        }
                    },
                )
                exported?.let { file ->
                    GlassButton(
                        label = "send",
                        modifier = Modifier.weight(1f),
                        onClick = { Workbook.share(context, file) },
                    )
                }
            }
            exportNote?.let {
                Spacer(Modifier.height(Space.tight))
                Text(it, style = Type.footnote, color = glass.inkMuted)
            }
        }

        Spacer(Modifier.height(110.dp))
    }

    if (showHistory) {
        FullHistory(
            plays = dashboard?.feed.orEmpty(),
            onClose = { showHistory = false },
            onPlay = { play -> scope.launch { Router.play(context, play.title, play.artist) } },
            onRemove = { play ->
                scope.launch {
                    withContext(Dispatchers.IO) { db.delete(play.id) }
                    reload++
                }
            },
        )
    }

    if (confirming) {
        ConfirmErase(
            days = eraseDays,
            onCancel = { confirming = false },
            onConfirm = {
                confirming = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        db.eraseFeedOlderThan(eraseDays)
                        db.sweepArt(context)
                        db.compact()
                    }
                    reload++
                }
            },
        )
    }
}

@Composable
private fun FullHistory(
    plays: List<Play>,
    onClose: () -> Unit,
    onPlay: (Play) -> Unit,
    onRemove: (Play) -> Unit,
) {
    val glass = LocalGlass.current
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
                    .padding(horizontal = Space.large),
            ) {
                Spacer(Modifier.height(Space.medium))
                Text("everything you played", style = Type.wordmark, color = glass.ink)
                Text("${plays.size} plays, newest first", style = Type.body, color = glass.inkMuted)
                Spacer(Modifier.height(Space.medium))

                var open by remember { mutableStateOf<Long?>(null) }
                val days = remember(plays) { plays.groupBy { dayLabel(it.startedAt) } }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(Space.tight),
                    verticalArrangement = Arrangement.spacedBy(Space.small),
                    modifier = Modifier.weight(1f),
                ) {
                    for ((day, entries) in days) {
                        item(key = day, span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                day,
                                style = Type.blockTitle,
                                color = glass.inkMuted,
                                modifier = Modifier.padding(top = Space.small),
                            )
                        }
                        items(entries, key = { it.id }) { play ->
                            PlayTile(
                                play = play,
                                live = false,
                                stamp = timeLabel(play.startedAt),
                                open = open == play.id,
                                onTap = { open = if (open == play.id) null else play.id },
                                onPlay = { onPlay(play) },
                                onRemove = {
                                    open = null
                                    onRemove(play)
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.small))

                GlassButton(
                    label = "done",
                    filled = true,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onClose,
                )
                Spacer(Modifier.height(Space.medium))
            }
        }
    }
}

private fun dayLabel(millis: Long): String {
    val today = Days.of(System.currentTimeMillis())
    val day = Days.of(millis)
    return when (day) {
        today -> "today"
        Days.ago(1) -> "yesterday"
        else -> SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(Date(millis)).lowercase()
    }
}

private fun timeLabel(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

@Composable
private fun ConfirmErase(days: Int, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val glass = LocalGlass.current
    Dialog(onDismissRequest = onCancel) {
        GlassCard(strong = true) {
            Text("erase older than $days days?", style = Type.title, color = glass.ink)
            Text(
                "this removes those rows from the feed only. every number in your stats stays exactly " +
                    "as it is. they are the absolute record.",
                style = Type.footnote,
                color = glass.inkMuted,
                modifier = Modifier.padding(top = 6.dp, bottom = Space.medium),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                GlassButton(label = "cancel", modifier = Modifier.weight(1f), onClick = onCancel)
                GlassButton(label = "erase", filled = true, modifier = Modifier.weight(1f), onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    val glass = LocalGlass.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(label, style = Type.footnote, color = glass.inkMuted)
        Text(
            value,
            style = Type.callout,
            color = glass.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    Column(
        modifier
            .clip(Shapes.tile)
            .background(glass.tile)
            .border(1.dp, glass.strokeSoft, Shapes.tile)
            .padding(Space.medium),
    ) {
        Text(value, style = Type.figure, color = Color.White)
        Text(
            label,
            style = Type.caps,
            color = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun Chart(
    title: String,
    rows: List<Tally>,
    icons: Boolean = false,
    label: (String) -> String = { it },
    onRowClick: ((Tally) -> Unit)? = null,
) {
    if (rows.isEmpty()) return
    val glass = LocalGlass.current
    val peak = rows.maxOf { it.listenedMs }.coerceAtLeast(1)
    Spacer(Modifier.height(Space.medium))
    GlassCard {
        SectionHeader(title)
        for (row in rows) {
            Column(
                Modifier
                    .then(
                        if (onRowClick != null) {
                            Modifier.clickable { onRowClick(row) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(vertical = Space.tight),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (icons) {
                        SourceIcon(row.label, size = 22.dp)
                        Spacer(Modifier.size(Space.small))
                    }
                    Text(
                        label(row.label),
                        style = Type.callout,
                        color = glass.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(Stats.minutes(row.listenedMs), style = Type.footnote, color = glass.inkMuted)
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(glass.fill),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(row.listenedMs.toFloat() / peak)
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(glass.hot)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayTile(
    play: Play,
    live: Boolean,
    stamp: String,
    open: Boolean,
    onTap: () -> Unit,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    val glass = LocalGlass.current
    val context = LocalContext.current
    var bitmap by remember(play.id) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(play.id) {
        bitmap = play.artwork?.let { Artwork.loadUrl(context, it) }?.asImageBitmap()
    }

    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(Shapes.tile)
                .background(glass.fill)
                .border(1.dp, glass.strokeSoft, Shapes.tile)
                .pointerInput(play.id) {
                    detectTapGestures(onTap = { onTap() }, onDoubleTap = { onPlay() })
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
                Text(play.title.take(1).uppercase(), style = Type.title, color = glass.inkFaint)
            }

            if (!open) {
                if (live) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(glass.accent),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(stamp, style = Type.footnote, color = Color.White)
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(Stats.clock(play.listenedMs), style = Type.footnote, color = Color.White)
                }
            }

            if (open) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    IconAction(glyph = Glyph.PLAY, filled = true, diameter = 36.dp, onClick = onPlay)
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .pointerInput(play.id) {
                                detectTapGestures(onTap = { onRemove() })
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("×", style = Type.headline, color = Color.White)
                    }
                }
            }
        }

        Text(
            play.title,
            style = Type.footnote,
            color = glass.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceIcon(play.source, size = 11.dp)
            Spacer(Modifier.size(3.dp))
            Text(
                play.artist ?: "unknown artist",
                style = Type.callout,
                color = glass.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

private const val FEED_PREVIEW = 12

private fun appLabel(context: Context, pkg: String): String = runCatching {
    val manager = context.packageManager
    manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString().lowercase()
}.getOrDefault(pkg.substringAfterLast('.'))
