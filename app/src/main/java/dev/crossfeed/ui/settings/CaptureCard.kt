package dev.crossfeed.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.history.VideoTitles
import dev.crossfeed.core.lyrics.Meaning
import dev.crossfeed.ui.GlassCard
import dev.crossfeed.ui.GlassButton
import dev.crossfeed.ui.SectionHeader
import dev.crossfeed.ui.ToggleRow
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.launch

@Composable
fun CaptureCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val prefs = remember { Prefs(context) }
    var browserMusic by remember { mutableStateOf(prefs.countBrowserMusic) }

    GlassCard {
        SectionHeader("what counts")
        Text(
            "music apps are always counted. browsers and video apps are not, because most of what " +
                "plays there is not music.",
            style = Type.footnote,
            color = glass.inkMuted,
            modifier = Modifier.padding(bottom = Space.tight),
        )
        ToggleRow(
            title = "count music played in a browser",
            subtitle = "a video only counts once its title matches a real record, so lectures and " +
                "clips are left out. youtube in the app is included too",
            checked = browserMusic,
            onChange = {
                browserMusic = it
                prefs.countBrowserMusic = it
                VideoTitles.forget()
            },
        )
    }
}

@Composable
fun LyricsCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val prefs = remember { Prefs(context) }
    var translate by remember { mutableStateOf(prefs.translateLyrics) }

    GlassCard {
        SectionHeader("sing along")
        Text(
            "translation happens on the phone. a language pack is downloaded the first time and " +
                "works offline after that, and no lyric is ever sent anywhere.",
            style = Type.footnote,
            color = glass.inkMuted,
            modifier = Modifier.padding(bottom = Space.tight),
        )
        ToggleRow(
            title = "offer what it means",
            subtitle = "puts a switch in sing along that shows each line translated underneath",
            checked = translate,
            onChange = {
                translate = it
                prefs.translateLyrics = it
            },
        )
    }
}

/**
 * Packs kept ahead of time, so a song in another language does not wait on a download. Nothing is
 * fetched without being asked for: thirty megabytes each adds up quickly.
 */
@Composable
fun LanguagePacksCard() {
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    var kept by remember { mutableStateOf(emptySet<String>()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf(false) }

    LaunchedEffect(reload) { kept = runCatching { Meaning.kept() }.getOrDefault(emptySet()) }

    GlassCard {
        SectionHeader("language packs") {
            Text(kept.size.toString() + " kept", style = Type.footnote, color = glass.inkFaint)
        }
        Text(
            "each pack is about thirty megabytes and every language pairs through english, so keep " +
                "english plus the ones you actually listen in. downloaded on wi-fi only.",
            style = Type.footnote,
            color = glass.inkMuted,
        )
        note?.let {
            Text(it, style = Type.footnote, color = glass.accent, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.height(Space.tight))
        GlassButton(
            label = if (open) "done" else "choose languages",
            modifier = Modifier.fillMaxWidth(),
            onClick = { open = !open },
        )
        if (!open) return@GlassCard

        for ((tag, name) in Meaning.commonLanguages) {
            val here = tag in kept
            Row(
                Modifier.fillMaxWidth().padding(top = Space.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    name,
                    style = Type.headline,
                    color = if (here) glass.ink else glass.inkMuted,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    label = when {
                        busy == tag -> "…"
                        here -> "remove"
                        else -> "keep"
                    },
                    compact = true,
                    enabled = busy == null,
                    onClick = {
                        busy = tag
                        scope.launch {
                            note = if (here) {
                                Meaning.drop(tag)
                                name + " removed"
                            } else if (Meaning.fetch(tag)) {
                                name + " is ready"
                            } else {
                                "could not fetch " + name + ". wi-fi?"
                            }
                            busy = null
                            reload++
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(Space.tight))
    }
}
