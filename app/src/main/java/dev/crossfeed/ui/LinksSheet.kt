package dev.crossfeed.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.crossfeed.core.Links
import dev.crossfeed.core.Opener
import dev.crossfeed.core.ServiceLink
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type

/**
 * One song, every service. Each row offers the app itself and the plain link, because sharing a
 * song usually means pasting it somewhere rather than opening it here.
 */
@Composable
fun LinksSheet(title: String, artist: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    var links by remember { mutableStateOf(emptyList<ServiceLink>()) }

    LaunchedEffect(title, artist) {
        links = runCatching { Links.forTrack(context, title, artist) }.getOrDefault(emptyList())
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(Shapes.card)
                .background(glass.sheet)
                .padding(Space.large)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(title, style = Type.title, color = glass.ink)
            artist?.let { Text(it, style = Type.footnote, color = glass.inkMuted) }
            Spacer(Modifier.height(Space.medium))

            if (links.isEmpty()) {
                Text("looking these up…", style = Type.body, color = glass.inkMuted)
            }

            for (link in links) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.tight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.small),
                ) {
                    AppMark(link, onClick = { Opener.open(context, link.platform, link.url) })
                    Column(Modifier.weight(1f)) {
                        Text(link.platform.label, style = Type.headline, color = glass.ink)
                        Text(
                            if (link.exact) "the song itself" else "a search that lands on it",
                            style = Type.footnote,
                            color = glass.inkFaint,
                        )
                    }
                    IconAction(glyph = Glyph.COPY, diameter = 36.dp) { copyLink(context, link.url) }
                }
            }

            Spacer(Modifier.height(Space.small))
            GlassButton(label = "done", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

/** The service's own icon, so the row is recognisable before it is read. */
@Composable
private fun AppMark(link: ServiceLink, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(glass.fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PlatformGlyph(link.platform, size = 26.dp)
    }
}

/** Sharing a song usually ends in a paste, so the link itself is always one tap away. */
fun copyLink(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("crossfeed", url))
    Toast.makeText(context, "link copied", Toast.LENGTH_SHORT).show()
}
