package dev.crossfeed.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.history.VideoTitles
import dev.crossfeed.ui.GlassCard
import dev.crossfeed.ui.SectionHeader
import dev.crossfeed.ui.ToggleRow
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type

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
