package dev.crossfeed.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import dev.crossfeed.ui.theme.LocalGlass

enum class Glyph { PLAY, PAUSE, NEXT, PREVIOUS, PLUS, BOOKMARK, BELL }

@Composable
fun IconAction(
    glyph: Glyph,
    filled: Boolean = false,
    active: Boolean = false,
    diameter: androidx.compose.ui.unit.Dp = 38.dp,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    val ink = if (filled) Color.White else if (active) glass.accent else glass.ink
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .then(
                if (filled) {
                    Modifier.background(Brush.linearGradient(glass.hot))
                } else {
                    Modifier.background(glass.fill)
                },
            )
            .border(1.dp, if (filled) Color.Transparent else glass.strokeSoft, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.45f)) {
            val w = size.width
            val h = size.height
            when (glyph) {
                Glyph.PLAY -> {
                    val path = Path().apply {
                        moveTo(w * 0.18f, 0f)
                        lineTo(w, h * 0.5f)
                        lineTo(w * 0.18f, h)
                        close()
                    }
                    drawPath(path, ink)
                }

                Glyph.PAUSE -> {
                    val bar = w * 0.26f
                    drawRect(ink, Offset(w * 0.14f, 0f), androidx.compose.ui.geometry.Size(bar, h))
                    drawRect(ink, Offset(w * 0.60f, 0f), androidx.compose.ui.geometry.Size(bar, h))
                }

                Glyph.NEXT, Glyph.PREVIOUS -> {
                    val flip = glyph == Glyph.PREVIOUS
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w * 0.62f, h * 0.5f)
                        lineTo(0f, h)
                        close()
                    }
                    if (flip) {
                        scale(-1f, 1f, Offset(w * 0.5f, h * 0.5f)) {
                            drawPath(path, ink)
                            drawRect(ink, Offset(w * 0.72f, 0f), androidx.compose.ui.geometry.Size(w * 0.2f, h))
                        }
                    } else {
                        drawPath(path, ink)
                        drawRect(ink, Offset(w * 0.72f, 0f), androidx.compose.ui.geometry.Size(w * 0.2f, h))
                    }
                }

                Glyph.PLUS -> {
                    val thick = w * 0.16f
                    drawRect(ink, Offset(w * 0.5f - thick / 2, 0f), androidx.compose.ui.geometry.Size(thick, h))
                    drawRect(ink, Offset(0f, h * 0.5f - thick / 2), androidx.compose.ui.geometry.Size(w, thick))
                }

                Glyph.BOOKMARK -> {
                    val path = Path().apply {
                        moveTo(w * 0.2f, 0f)
                        lineTo(w * 0.8f, 0f)
                        lineTo(w * 0.8f, h)
                        lineTo(w * 0.5f, h * 0.72f)
                        lineTo(w * 0.2f, h)
                        close()
                    }
                    if (active) drawPath(path, ink) else drawPath(path, ink, style = Stroke(width = w * 0.11f))
                }

                Glyph.BELL -> {
                    val path = Path().apply {
                        moveTo(w * 0.16f, h * 0.72f)
                        lineTo(w * 0.84f, h * 0.72f)
                        lineTo(w * 0.72f, h * 0.56f)
                        lineTo(w * 0.72f, h * 0.34f)
                        cubicTo(w * 0.72f, h * 0.1f, w * 0.28f, h * 0.1f, w * 0.28f, h * 0.34f)
                        lineTo(w * 0.28f, h * 0.56f)
                        close()
                    }
                    drawPath(path, ink)
                    drawCircle(ink, w * 0.11f, Offset(w * 0.5f, h * 0.88f))
                }
            }
        }
    }
}
