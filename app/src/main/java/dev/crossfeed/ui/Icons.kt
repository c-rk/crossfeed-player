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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import dev.crossfeed.ui.theme.LocalGlass

enum class Glyph { PLAY, PAUSE, NEXT, PREVIOUS, PLUS, BOOKMARK, BELL, LINK, COPY, GRID, LIST, CLOSE, DEVICE, SEARCH, REPLY }

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
        Mark(glyph, diameter * 0.45f, ink, active)
    }
}

/** The tab bar wears a gear rather than the word, so the row stays short. */
@Composable
fun Gear(active: Boolean) {
    val glass = LocalGlass.current
    val ink = if (active) Color.White else glass.ink
    Canvas(Modifier.size(19.dp)) {
        val radius = size.minDimension * 0.30f
        val centre = Offset(size.width / 2f, size.height / 2f)
        drawCircle(ink, radius, centre, style = Stroke(width = size.width * 0.13f))
        val tooth = androidx.compose.ui.geometry.Size(size.width * 0.13f, size.height * 0.20f)
        repeat(8) { index ->
            rotate(index * 45f, centre) {
                drawRect(ink, Offset(centre.x - tooth.width / 2f, 0f), tooth)
            }
        }
    }
}


/**
 * One icon, drawn rather than shipped, so a glyph can be any size and any colour without a
 * drawable per pairing.
 */
@Composable
fun Mark(
    glyph: Glyph,
    side: androidx.compose.ui.unit.Dp,
    tint: Color,
    active: Boolean = false,
) {
    val ink = tint
    Canvas(Modifier.size(side)) {
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

                Glyph.LINK -> {
                    val stroke = Stroke(width = w * 0.12f)
                    val arm = Path().apply {
                        moveTo(w * 0.42f, h * 0.28f)
                        lineTo(w * 0.62f, h * 0.08f)
                        cubicTo(w * 0.82f, h * -0.06f, w * 1.06f, h * 0.18f, w * 0.92f, h * 0.38f)
                        lineTo(w * 0.72f, h * 0.58f)
                    }
                    drawPath(arm, ink, style = stroke)
                    scale(-1f, -1f, Offset(w * 0.5f, h * 0.5f)) { drawPath(arm, ink, style = stroke) }
                    drawLine(ink, Offset(w * 0.36f, h * 0.64f), Offset(w * 0.64f, h * 0.36f), w * 0.12f)
                }

                Glyph.COPY -> {
                    val stroke = Stroke(width = w * 0.11f)
                    drawRoundRect(
                        ink,
                        Offset(0f, h * 0.24f),
                        androidx.compose.ui.geometry.Size(w * 0.62f, h * 0.76f),
                        androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
                        style = stroke,
                    )
                    drawRoundRect(
                        ink,
                        Offset(w * 0.30f, 0f),
                        androidx.compose.ui.geometry.Size(w * 0.70f, h * 0.70f),
                        androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
                        style = stroke,
                    )
                }

                Glyph.GRID -> {
                    val cell = w * 0.40f
                    for (x in 0..1) for (y in 0..1) {
                        drawRect(
                            ink,
                            Offset(x * (w - cell), y * (h - cell)),
                            androidx.compose.ui.geometry.Size(cell, cell),
                        )
                    }
                }

                Glyph.LIST -> {
                    val bar = h * 0.18f
                    for (row in 0..2) {
                        drawRect(
                            ink,
                            Offset(0f, row * (h - bar) / 2f),
                            androidx.compose.ui.geometry.Size(w, bar),
                        )
                    }
                }

                Glyph.CLOSE -> {
                    val stroke = Stroke(width = w * 0.14f)
                    val cross = Path().apply {
                        moveTo(w * 0.12f, h * 0.12f)
                        lineTo(w * 0.88f, h * 0.88f)
                        moveTo(w * 0.88f, h * 0.12f)
                        lineTo(w * 0.12f, h * 0.88f)
                    }
                    drawPath(cross, ink, style = stroke)
                }

                Glyph.DEVICE -> {
                    val stroke = Stroke(width = w * 0.1f)
                    drawRoundRect(
                        ink,
                        Offset(w * 0.26f, h * 0.06f),
                        androidx.compose.ui.geometry.Size(w * 0.48f, h * 0.88f),
                        androidx.compose.ui.geometry.CornerRadius(w * 0.12f, w * 0.12f),
                        style = stroke,
                    )
                    drawCircle(ink, w * 0.05f, Offset(w * 0.5f, h * 0.76f))
                }

                Glyph.SEARCH -> {
                    val stroke = Stroke(width = w * 0.13f)
                    drawCircle(ink, w * 0.3f, Offset(w * 0.42f, h * 0.42f), style = stroke)
                    drawLine(
                        ink,
                        Offset(w * 0.66f, h * 0.66f),
                        Offset(w * 0.94f, h * 0.94f),
                        strokeWidth = w * 0.13f,
                    )
                }

                Glyph.REPLY -> {
                    val stroke = Stroke(width = w * 0.12f)
                    val path = Path().apply {
                        moveTo(w * 0.38f, h * 0.16f)
                        lineTo(w * 0.06f, h * 0.46f)
                        lineTo(w * 0.38f, h * 0.76f)
                        moveTo(w * 0.06f, h * 0.46f)
                        lineTo(w * 0.7f, h * 0.46f)
                        cubicTo(w * 0.98f, h * 0.46f, w * 0.98f, h * 0.94f, w * 0.62f, h * 0.94f)
                    }
                    drawPath(path, ink, style = stroke)
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