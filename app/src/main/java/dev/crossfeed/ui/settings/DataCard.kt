package dev.crossfeed.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.crossfeed.core.export.Export
import dev.crossfeed.core.export.Bundle
import dev.crossfeed.core.export.Restore
import dev.crossfeed.core.export.Workbook
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.Stats
import dev.crossfeed.ui.ConfirmErase
import dev.crossfeed.ui.GlassButton
import dev.crossfeed.ui.GlassCard
import dev.crossfeed.ui.SectionHeader
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The last export of each kind, kept beyond the settings page so leaving and coming back does not
 * forget it. A repeat is one made within a couple of minutes of the last, or with nothing new in
 * the diary since, and only while the file it would duplicate still exists.
 */
object Exports {
    data class Last(val file: Export, val at: Long, val mark: String)

    private val last = mutableMapOf<String, Last>()
    private const val SOON_MS = 2 * 60_000L

    fun remember(kind: String, file: Export, mark: String) {
        last[kind] = Last(file, System.currentTimeMillis(), mark)
    }

    fun repeatOf(context: android.content.Context, kind: String, mark: String): Last? {
        val previous = last[kind] ?: return null
        val recent = System.currentTimeMillis() - previous.at < SOON_MS
        if (!recent && previous.mark != mark) return null
        // deleted from downloads since, so a fresh one is what is wanted
        val present = runCatching {
            context.contentResolver.openFileDescriptor(previous.file.uri, "r")?.use { it.statSize > 0 }
        }.getOrNull() == true
        return previous.takeIf { present }
    }

    fun ago(at: Long): String {
        val gone = System.currentTimeMillis() - at
        return when {
            gone < 60_000L -> "just now"
            gone < 3600_000L -> "${gone / 60_000L}m ago"
            else -> "${gone / 3600_000L}h ago"
        }
    }
}

@Composable
fun DataCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val db = remember { HistoryDb.get(context) }

    var dbSize by remember { mutableLongStateOf(0L) }
    var feedRows by remember { mutableIntStateOf(0) }
    var eraseDays by remember { mutableIntStateOf(30) }
    var confirming by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var exported by remember { mutableStateOf<Export?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var replacing by remember { mutableStateOf<android.net.Uri?>(null) }

    val wholePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) replacing = uri
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                runCatching { Restore.fromFile(context, uri) }
                    .onSuccess {
                        note = Restore.describe(it)
                        reload++
                    }
                    .onFailure { note = it.message ?: "could not read that file" }
                importing = false
            }
        }
    }

    LaunchedEffect(reload) {
        withContext(Dispatchers.IO) {
            dbSize = db.sizeBytes(context)
            feedRows = db.feedCount()
        }
    }

    GlassCard {
        SectionHeader("storage")
        Text(
            "the feed lives in listening.db, private to crossfeed. erasing it frees space. " +
                "your stats are kept whatever you delete.",
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
                "eleven sheets, saved to downloads. the zip carries the artwork with them, so " +
                    "the diary opens whole on another phone.",
            style = Type.footnote,
            color = glass.inkMuted,
        )
        Spacer(Modifier.height(Space.small))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
            // a second press straight after the first, or with nothing new in the diary since,
            // points at the file that already exists instead of writing the same one again
            fun write(kind: String, make: suspend () -> Export) {
                exporting = true
                scope.launch {
                    val mark = withContext(Dispatchers.IO) { db.changeMark() }
                    val already = Exports.repeatOf(context, kind, mark)
                    if (already != null) {
                        exported = already.file
                        note = "already exported " + Exports.ago(already.at) + ": ${already.file.path}"
                    } else {
                        runCatching { make() }
                            .onSuccess {
                                exported = it
                                Exports.remember(kind, it, mark)
                                note = "exported ${it.path} · ${Stats.bytes(it.bytes)}"
                            }
                            .onFailure { note = "could not write the file" }
                    }
                    exporting = false
                }
            }
            GlassButton(
                label = if (exporting) "writing…" else "everything",
                filled = true,
                enabled = !exporting,
                modifier = Modifier.weight(1f),
                onClick = { write("zip") { Workbook.bundle(context) } },
            )
            GlassButton(
                label = "sheet only",
                enabled = !exporting,
                modifier = Modifier.weight(1f),
                onClick = { write("sheet") { Workbook.export(context) } },
            )
        }
        exported?.let { file ->
            Spacer(Modifier.height(Space.small))
            GlassButton(
                label = "send " + file.name.substringAfterLast('.'),
                modifier = Modifier.fillMaxWidth(),
                onClick = { Workbook.share(context, file) },
            )
        }
        Spacer(Modifier.height(Space.small))
        GlassButton(
            label = if (importing) "reading…" else "bring a diary back",
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                picker.launch(
                    arrayOf(Bundle.MIME, Workbook.MIME, "application/octet-stream", "*/*"),
                )
            },
        )
        Spacer(Modifier.height(Space.tight))
        GlassButton(
            label = if (importing) "reading…" else "replace with a backup",
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
            onClick = { wholePicker.launch(arrayOf(Bundle.MIME, "application/octet-stream", "*/*")) },
        )
        Text(
            "the first merges: it adds what is missing and leaves what is here. the second " +
                "restores: the diary in the zip becomes the diary on this phone, saves, genres, " +
                "artwork and all. use that one when you move phones.",
            style = Type.footnote,
            color = glass.inkFaint,
            modifier = Modifier.padding(top = Space.tight),
        )
        replacing?.let { uri ->
            Spacer(Modifier.height(Space.small))
            GlassCard(strong = true) {
                Text("replace everything?", style = Type.section, color = glass.ink)
                Text(
                    "the diary on this phone is thrown away and the one in that zip takes its " +
                        "place. there is no undo, so export first if there is anything here you " +
                        "have not got a copy of.",
                    style = Type.footnote,
                    color = glass.inkMuted,
                    modifier = Modifier.padding(vertical = Space.tight),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Space.small)) {
                    GlassButton(
                        label = "replace",
                        filled = true,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val target = uri
                            replacing = null
                            importing = true
                            scope.launch {
                                runCatching { Restore.whole(context, target) }
                                    .onSuccess {
                                        note = "restored ${it.added} plays and ${it.sleeves} sleeves"
                                        reload++
                                    }
                                    .onFailure { note = it.message ?: "could not read that backup" }
                                importing = false
                            }
                        },
                    )
                    GlassButton(
                        label = "keep mine",
                        modifier = Modifier.weight(1f),
                        onClick = { replacing = null },
                    )
                }
            }
        }

        note?.let {
            Spacer(Modifier.height(Space.tight))
            Text(it, style = Type.footnote, color = glass.inkMuted)
        }
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


/** What the file picker called the thing, when it will say. */
private fun fileName(context: android.content.Context, uri: android.net.Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
    }
}.getOrNull()
