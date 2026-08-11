package dev.crossfeed.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.crossfeed.core.Platform
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Type
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun rememberAppIcon(pkg: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(pkg) {
        runCatching {
            context.packageManager.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap()
        }.getOrNull()
    }
}

@Composable
fun IconWheel(
    platforms: List<Platform>,
    selected: List<Platform>,
    installed: (Platform) -> Boolean,
    onToggle: (Platform) -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 300.dp,
) {
    val glass = LocalGlass.current
    var spin by remember { mutableFloatStateOf(0f) }
    val radius = diameter / 2 - 44.dp
    val step = 360f / platforms.size

    Box(
        modifier
            .size(diameter)
            .pointerInput(platforms.size) {
                detectDragGestures { change, _ ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val from = change.previousPosition - center
                    val to = change.position - center
                    val delta = Math.toDegrees(
                        (atan2(to.y, to.x) - atan2(from.y, from.x)).toDouble(),
                    ).toFloat()
                    spin += delta
                    change.consume()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(diameter)
                .drawBehind {
                    val r = size.minDimension / 2f
                    drawCircle(
                        color = glass.strokeSoft,
                        radius = r - 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.Transparent, glass.fill.copy(alpha = 0.16f)),
                            radius = r,
                        ),
                        radius = r - 2f,
                    )
                    repeat(48) { index ->
                        val angle = Math.toRadians((index * 7.5 + spin).toDouble())
                        val outer = r - 8f
                        val inner = if (index % 4 == 0) r - 22f else r - 15f
                        drawLine(
                            color = glass.stroke.copy(alpha = if (index % 4 == 0) 0.7f else 0.3f),
                            start = center + Offset(
                                (outer * cos(angle)).toFloat(),
                                (outer * sin(angle)).toFloat(),
                            ),
                            end = center + Offset(
                                (inner * cos(angle)).toFloat(),
                                (inner * sin(angle)).toFloat(),
                            ),
                            strokeWidth = 1.5f,
                        )
                    }
                },
        )

        platforms.forEachIndexed { index, platform ->
            val angle = Math.toRadians((index * step - 90f + spin).toDouble())
            val isSelected = platform in selected
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1f else 0.82f,
                animationSpec = spring(dampingRatio = 0.55f),
                label = "scale",
            )
            WheelIcon(
                platform = platform,
                selected = isSelected,
                available = installed(platform),
                order = if (isSelected && selected.size > 1) selected.indexOf(platform) + 1 else null,
                modifier = Modifier
                    .offset(
                        x = radius * cos(angle).toFloat(),
                        y = radius * sin(angle).toFloat(),
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                onClick = { onToggle(platform) },
            )
        }

        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(listOf(glass.fillStrong, glass.fill)),
                )
                .border(1.dp, glass.stroke, CircleShape)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    selected.isEmpty() -> "pick an app"
                    selected.size == 1 -> selected.first().label
                    else -> "${selected.size} apps\nask each time"
                },
                style = Type.callout,
                color = glass.ink,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun WheelIcon(
    platform: Platform,
    selected: Boolean,
    available: Boolean,
    order: Int?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    val icon = rememberAppIcon(platform.pkg)
    Box(modifier.size(66.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier.background(
                            Brush.linearGradient(glass.hot.map { it.copy(alpha = 0.45f) }),
                        )
                    } else {
                        Modifier.background(glass.fill)
                    },
                )
                .border(
                    BorderStroke(
                        width = if (selected) 2.5.dp else 1.dp,
                        brush = if (selected) {
                            Brush.linearGradient(glass.hot)
                        } else {
                            SolidColor(glass.strokeSoft)
                        },
                    ),
                    CircleShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = platform.label,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .graphicsLayer { alpha = if (available) 1f else 0.4f },
                )
            } else {
                Text(
                    platform.label.take(1).uppercase(),
                    style = Type.title,
                    color = if (available) glass.ink else glass.inkFaint,
                )
            }
        }
        if (order != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(glass.cool)),
                contentAlignment = Alignment.Center,
            ) {
                Text("$order", style = Type.caps, color = Color.White)
            }
        }
    }
}
