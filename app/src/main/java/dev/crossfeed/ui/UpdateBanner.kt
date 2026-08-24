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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch

/**
 * A single line at the top of the page, above everything rather than over it, so it can never sit
 * on a card or hide a control. It is only here when there is something to say.
 *
 * A new version takes precedence over a written notice, since it is the more useful of the two and
 * two lines would be one too many.
 */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { Update.check(context) }

    val release = Update.newer
    val notice = Update.notice
    if (release == null && notice == null) return

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
        if (release != null) {
            Text(
                Update.trouble ?: if (Update.fetching) {
                    "getting ${release.version}…"
                } else {
                    "${release.version} is out"
                },
                style = Type.footnote,
                color = if (Update.trouble != null) glass.warning else glass.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            GlassButton(
                label = if (Update.trouble != null) "open" else "update",
                filled = true,
                compact = true,
                enabled = !Update.fetching,
                onClick = {
                    if (Update.trouble != null) {
                        Update.openPage(context)
                    } else {
                        scope.launch { Update.fetch(context, release) }
                    }
                },
            )
            IconAction(glyph = Glyph.CLOSE, diameter = 26.dp) { Update.setAside(context) }
            return@Row
        }

        notice ?: return@Row
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
                label = "open",
                filled = true,
                compact = true,
                onClick = { Opener.openWeb(context, link) },
            )
        }
        IconAction(glyph = Glyph.CLOSE, diameter = 26.dp) { Update.dismiss(context) }
    }
}
