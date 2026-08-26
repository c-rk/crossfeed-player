package dev.crossfeed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.crossfeed.core.LibraryItem
import dev.crossfeed.core.Platform
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type

@Composable
fun TargetChooser(
    item: LibraryItem,
    targets: List<Platform>,
    onPick: (Platform) -> Unit,
    onDismiss: () -> Unit,
) {
    val glass = LocalGlass.current
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(strong = true) {
            Text("open where?", style = Type.title, color = glass.ink)
            Text(
                item.title,
                style = Type.footnote,
                color = glass.inkFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, bottom = Space.medium),
            )
            for (platform in targets) {
                PlatformRow(platform) { onPick(platform) }
            }
        }
    }
}

@Composable
fun PlatformRow(platform: Platform, badge: String? = null, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.tile)
            .clickable(onClick = onClick)
            .padding(vertical = Space.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(glass.fill)
                .border(1.dp, glass.strokeSoft, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            PlatformGlyph(platform, size = 26.dp)
        }
        Text(
            platform.label,
            style = Type.headline,
            color = glass.ink,
            modifier = Modifier
                .weight(1f)
                .padding(start = Space.medium),
        )
        if (badge != null) {
            GlassChip(label = badge, selected = badge == "exact")
        }
    }
}

@Composable
fun ColumnSpacer(height: androidx.compose.ui.unit.Dp = Space.small) {
    Column { Spacer(Modifier.height(height)) }
}
