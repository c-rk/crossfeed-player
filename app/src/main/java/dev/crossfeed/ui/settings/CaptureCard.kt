package dev.crossfeed.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import dev.crossfeed.ui.GlassChip
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
            subtitle = "a video only counts once it names an artist the catalogue lists, so " +
                "lectures and clips are left out. checking that sends the title to apple",
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

    GlassCard {
        SectionHeader("sing along")
        Text(
            "sing along has a switch that puts a translation under each line. it runs on the " +
                "phone: a language pack is downloaded the first time and works offline after " +
                "that, and no lyric is ever sent anywhere.",
            style = Type.footnote,
            color = glass.inkMuted,
            modifier = Modifier.padding(bottom = Space.tight),
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

/**
 * The one place that says what is asked of anyone else. Every question here names a track, so each
 * is a small disclosure of the diary and each waits to be turned on.
 */
@Composable
fun DisclosureCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val prefs = remember { Prefs(context) }
    var genres by remember { mutableStateOf(prefs.lookUpGenres) }

    GlassCard {
        SectionHeader("what leaves the phone")
        Text(
            "your history is never uploaded. some features do ask other people questions, and a " +
                "question names the track it is about, so they are listed here rather than buried.",
            style = Type.footnote,
            color = glass.inkMuted,
            modifier = Modifier.padding(bottom = Space.tight),
        )
        ToggleRow(
            title = "look up genres",
            subtitle = "asks apple what genre a track is, so the genres chart can fill in. it sends " +
                "the title and artist of tracks you have played",
            checked = genres,
            onChange = {
                genres = it
                prefs.lookUpGenres = it
            },
        )
        Text(
            "lyrics come from lrclib by title and artist. translation runs here and sends nothing. " +
                "artwork and links are looked up only for what you search for or share.",
            style = Type.footnote,
            color = glass.inkFaint,
            modifier = Modifier.padding(top = Space.small),
        )
    }
}

/** Common storefronts, plus whatever the phone says. Two letters is all apple wants. */
private val STORES = listOf(
    "in" to "india",
    "us" to "united states",
    "gb" to "united kingdom",
    "ca" to "canada",
    "au" to "australia",
    "de" to "germany",
    "fr" to "france",
    "jp" to "japan",
    "sg" to "singapore",
    "ae" to "emirates",
)

@Composable
fun StoreCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val prefs = remember { Prefs(context) }
    var chosen by remember { mutableStateOf(prefs.storeCountry) }

    GlassCard {
        SectionHeader("music store")
        Text(
            "which catalogue to search when crossfeed looks a song up or builds a link. a release " +
                "in one country is often missing from another, so this should match wherever you " +
                "actually subscribe.",
            style = Type.footnote,
            color = glass.inkMuted,
            modifier = Modifier.padding(bottom = Space.small),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
        ) {
            GlassChip(
                label = "my region",
                selected = chosen == null,
                onClick = {
                    chosen = null
                    prefs.storeCountry = null
                },
            )
            for ((code, name) in STORES) {
                GlassChip(
                    label = name,
                    selected = chosen == code,
                    onClick = {
                        chosen = code
                        prefs.storeCountry = code
                    },
                )
            }
        }
        Text(
            "searching " + (chosen ?: prefs.country).uppercase(),
            style = Type.caps,
            color = glass.accent,
            modifier = Modifier.padding(top = Space.small),
        )
    }
}
