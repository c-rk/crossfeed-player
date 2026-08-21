package dev.crossfeed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.crossfeed.core.Opener
import dev.crossfeed.core.net.Update
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type

/**
 * A single line at the top of the page, above everything rather than over it, so it can never sit
 * on a card or hide a control. It is only ever here when there is something to say.
 */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val notice = Update.notice

    LaunchedEffect(Unit) { Update.check(context) }

    if (notice == null) return

    val shape = RoundedCornerShape(50)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glass.fill)
            .border(1.dp, glass.strokeSoft, shape)
            .padding(start = Space.medium, end = Space.tight, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.tight),
    ) {
        Text(
            notice.text,
            style = Type.footnote,
            color = glass.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        notice.link?.let { link ->
            GlassButton(
                label = "get it",
                filled = true,
                compact = true,
                onClick = { Opener.openWeb(context, link) },
            )
        }
        IconAction(glyph = Glyph.CLOSE, diameter = 26.dp) { Update.dismiss(context) }
    }
}
