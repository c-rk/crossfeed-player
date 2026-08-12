package dev.crossfeed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.crossfeed.core.credits.CreditsSource
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type

@Composable
fun CreditsSheet(title: String, artist: String?, durationMs: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val db = remember { HistoryDb.get(context) }

    var credits by remember(title, artist) { mutableStateOf<List<HistoryDb.Credit>?>(null) }
    var looking by remember(title, artist) { mutableStateOf(true) }

    LaunchedEffect(title, artist) {
        looking = true
        credits = CreditsSource.find(context, title, artist, durationMs)
        looking = false
    }

    Dialog(onDismissRequest = onClose) {
        GlassCard(strong = true) {
            SectionHeader("credits")
            Text(
                title,
                style = Type.headline,
                color = glass.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                artist ?: "unknown artist",
                style = Type.footnote,
                color = glass.inkMuted,
                modifier = Modifier.padding(bottom = Space.small),
            )

            val people = credits
            when {
                looking -> Text("looking it up…", style = Type.body, color = glass.inkMuted)
                people.isNullOrEmpty() -> Text(
                    "no credits listed for this one.",
                    style = Type.body,
                    color = glass.inkMuted,
                )
                else -> {
                    Column(
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        for ((role, group) in people.groupBy { it.role }) {
                            Text(
                                role,
                                style = Type.caps,
                                color = glass.inkFaint,
                                modifier = Modifier.padding(top = Space.small),
                            )
                            for (credit in group) {
                                val seen = remember(credit.person) { db.appearances(credit.person) }
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        credit.person,
                                        style = Type.callout,
                                        color = glass.ink,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (seen > 1) {
                                        Text(
                                            "on $seen you've played",
                                            style = Type.footnote,
                                            color = glass.accent,
                                        )
                                    }
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
