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

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            importing = true
            scope.launch {
                runCatching { Restore.fromXlsx(context, uri) }
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
                                note = "exported ${it.path} · ${Stats.bytes(it.bytes)}"
                            }
                            .onFailure { note = "could not write the file" }
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
        Spacer(Modifier.height(Space.small))
        GlassButton(
            label = if (importing) "reading…" else "import .xlsx",
            enabled = !importing,
            modifier = Modifier.fillMaxWidth(),
            onClick = { picker.launch(arrayOf(Workbook.MIME, "application/octet-stream", "*/*")) },
        )
        Text(
            "brings a previous export back into this phone. plays already here are left alone.",
            style = Type.footnote,
            color = glass.inkFaint,
            modifier = Modifier.padding(top = Space.tight),
        )
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
