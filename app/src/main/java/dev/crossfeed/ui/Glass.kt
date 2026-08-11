package dev.crossfeed.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun Backdrop(content: @Composable () -> Unit) {
    val glass = LocalGlass.current
    val drift by rememberInfiniteTransition(label = "drift").animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(38000, easing = LinearEasing), RepeatMode.Restart),
        label = "drift",
    )

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(glass.wash))) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(90.dp) else Modifier)
                .background(
                    Brush.radialGradient(
                        colors = listOf(glass.hot[0].copy(alpha = if (glass.dark) 0.55f else 0.35f), Color.Transparent),
                        center = Offset(0.25f + 0.12f * cos(drift), 0.22f + 0.10f * sin(drift)) * 1000f,
                        radius = 560f,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(90.dp) else Modifier)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            glass.hot[1].copy(alpha = if (glass.dark) 0.3f else 0.22f),
                            Color.Transparent,
                        ),
                        center = Offset(0.78f + 0.14f * sin(drift * 0.8f), 0.62f + 0.12f * cos(drift * 0.6f)) * 1000f,
                        radius = 560f,
                    ),
                ),
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.card,
    strong: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlass.current
    Column(
        modifier
            .clip(shape)
            .then(if (strong) Modifier.background(glass.sheet) else Modifier)
            .background(
                Brush.verticalGradient(
                    if (strong) {
                        listOf(glass.fill, Color.Transparent)
                    } else {
                        listOf(glass.fill, glass.fill.copy(alpha = 0.44f))
                    },
                ),
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.verticalGradient(listOf(glass.stroke, glass.strokeSoft)),
                ),
                shape,
            )
            .padding(Space.large),
        content = content,
    )
}

@Composable
fun GlassButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    val shape = if (compact) Shapes.chip else Shapes.button
    val background = when {
        !enabled -> Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
        filled -> Brush.linearGradient(glass.hot)
        else -> Brush.verticalGradient(listOf(glass.fillStrong, glass.fill))
    }
    val ink = when {
        !enabled -> glass.inkFaint
        else -> Color.White
    }
    Box(
        modifier
            .clip(shape)
            .background(background)
            .border(
                BorderStroke(
                    1.dp,
                    when {
                        !enabled -> glass.strokeSoft
                        filled -> Color.Transparent
                        else -> glass.stroke
                    },
                ),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) Space.small + 2.dp else Space.large,
                vertical = if (compact) 7.dp else Space.small + 4.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (compact) Type.footnote else Type.headline,
            color = ink,
            maxLines = 1,
        )
    }
}

@Composable
fun GlassChip(
    label: String,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val glass = LocalGlass.current
    val shape = Shapes.chip
    Box(
        Modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(Brush.linearGradient(glass.hot))
                } else {
                    Modifier.background(glass.fill)
                },
            )
            .border(BorderStroke(1.dp, if (selected) Color.Transparent else glass.strokeSoft), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.small + 2.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            style = Type.caps,
            color = if (selected) Color.White else glass.ink,
        )
    }
}

@Composable
fun SectionHeader(text: String, trailing: (@Composable () -> Unit)? = null) {
    val glass = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = Space.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Type.blockTitle, color = glass.ink, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun tapless() = remember { MutableInteractionSource() }
