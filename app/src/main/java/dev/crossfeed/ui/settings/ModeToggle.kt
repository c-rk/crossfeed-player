package dev.crossfeed.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.crossfeed.ui.Glyph
import dev.crossfeed.ui.Mark
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Look
import dev.crossfeed.ui.theme.Shapes

/**
 * Dark or light, chosen rather than inherited, once the listener has an opinion.
 *
 * A sun and a moon rather than the words, because it is the one control on the page whose meaning
 * does not need reading.
 */
@Composable
fun ModeToggle() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    Row(
        Modifier
            .clip(Shapes.chip)
            .background(glass.p1)
            .border(1.dp, glass.bd, Shapes.chip)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (option in listOf("light", "dark")) {
            val on = Look.mode == option || (Look.mode == null && glass.dark == (option == "dark"))
            Box(
                Modifier
                    .size(30.dp)
                    .clip(Shapes.chip)
                    .background(if (on) glass.t1 else Color.Transparent)
                    .clickable { Look.setMode(context, option) },
                contentAlignment = Alignment.Center,
            ) {
                Mark(
                    if (option == "dark") Glyph.MOON else Glyph.SUN,
                    side = 15.dp,
                    tint = if (on) glass.bg else glass.t3,
                )
            }
        }
    }
}
