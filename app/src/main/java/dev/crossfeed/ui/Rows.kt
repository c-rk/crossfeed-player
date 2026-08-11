package dev.crossfeed.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.crossfeed.core.Artwork
import dev.crossfeed.core.LibraryItem
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type

@Composable
fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = Space.small + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.headline, color = glass.ink)
            if (subtitle != null) {
                Text(subtitle, style = Type.footnote, color = glass.inkMuted, modifier = Modifier.padding(top = 2.dp))
            }
        }
        IosSwitch(checked = checked, onChange = onChange)
    }
}

@Composable
fun IosSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val glass = LocalGlass.current
    val knob by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "knob",
    )
    Box(
        Modifier
            .width(51.dp)
            .height(31.dp)
            .clip(CircleShape)
            .background(if (checked) glass.accent else glass.fill)
            .border(1.dp, if (checked) Color.Transparent else glass.strokeSoft, CircleShape)
            .clickable { onChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size(27.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val glass = LocalGlass.current
    val shape = Shapes.field
    Box(
        modifier
            .clip(shape)
            .background(glass.fill)
            .border(1.dp, glass.strokeSoft, shape)
            .padding(horizontal = Space.medium + 2.dp, vertical = 14.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = Type.body, color = glass.inkFaint)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Type.body.copy(color = glass.ink),
            cursorBrush = SolidColor(glass.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun ArtworkTile(item: LibraryItem, size: androidx.compose.ui.unit.Dp = 52.dp) {
    val glass = LocalGlass.current
    val context = LocalContext.current
    var bitmap by remember(item.title, item.artist) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(item.title, item.artist) {
        bitmap = Artwork.load(context, item)?.asImageBitmap()
    }
    Box(
        Modifier
            .size(size)
            .clip(Shapes.chip)
            .background(glass.fill)
            .border(1.dp, glass.strokeSoft, Shapes.chip),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(Shapes.chip),
            )
        } else {
            Text(item.title.take(1).uppercase(), style = Type.headline, color = glass.inkFaint)
        }
    }
}

@Composable
fun TrackRow(item: LibraryItem, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtworkTile(item)
        Column(
            Modifier
                .weight(1f)
                .padding(start = Space.medium),
        ) {
            Text(item.title, style = Type.headline, color = glass.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(item.artist, item.album).joinToString(" · ").ifBlank { "unknown artist" },
                style = Type.footnote,
                color = glass.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.tight)) {
            for (source in item.sources) {
                GlassChip(label = source, selected = source != "apple music")
            }
        }
    }
}

@Composable
fun SourceIcon(pkg: String, size: androidx.compose.ui.unit.Dp = 18.dp) {
    val glass = LocalGlass.current
    val icon = rememberAppIcon(pkg)
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else {
        Box(
            Modifier.size(size).clip(CircleShape).background(glass.fill),
            contentAlignment = Alignment.Center,
        ) {
            Text(pkg.substringAfterLast('.').take(1).uppercase(), style = Type.caps, color = glass.inkFaint)
        }
    }
}
