/*
 * Crossfeed — the glass panel recipe.
 *
 * Every card in the design is this: p1 fill, 1dp bd border, an inset top
 * highlight, and a blur. Build it once; do not hand-roll per screen, and do NOT
 * add drop shadows (the only shadows in the design are on the dial's selected
 * nodes).
 *
 * Android note: there is no CSS backdrop-filter. Options, best first:
 *  1) API 31+  -> RenderEffect.createBlurEffect on a background layer
 *  2) Compose  -> Modifier.graphicsLayer + renderEffect on the content behind
 *  3) Fallback -> skip the blur and raise the fill alpha a step (p1 -> p2).
 * The design reads correctly without a true blur, because the accent blooms
 * behind it already provide the depth. Do not fake it with a gradient overlay.
 */

package org.tentkotta.crossfeed.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.tentkotta.crossfeed.ui.theme.Radius
import org.tentkotta.crossfeed.ui.theme.glass

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    radius: Dp = Radius.card,
    padding: PaddingValues = PaddingValues(horizontal = 13.dp, vertical = 12.dp),
    raised: Boolean = false,
    tint: Color? = null,
    tintBorder: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val c = glass()
    val shape = RoundedCornerShape(radius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(tint ?: if (raised) c.p2 else c.p1)
            .border(1.dp, tintBorder ?: c.bd, shape)
            .topHighlight(c.hi)
            .padding(padding),
        content = content
    )
}

/** The `inset 0 1px 0 hi` line that makes the panel read as glass, not paint. */
private fun Modifier.topHighlight(color: Color): Modifier = drawWithContent {
    drawContent()
    drawLine(
        color = color,
        start = Offset(0f, 0.5f),
        end = Offset(size.width, 0.5f),
        strokeWidth = 1f
    )
}

/**
 * The sharing card and the lyric strip use the sage variant — "other people".
 * Never pass the accent here.
 */
@Composable
fun SageCard(
    modifier: Modifier = Modifier,
    radius: Dp = Radius.card,
    padding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val c = glass()
    GlassCard(
        modifier = modifier,
        radius = radius,
        padding = padding,
        tint = c.sageTint,
        tintBorder = c.sageBorder,
        content = content
    )
}

/*
 * The accent bloom behind each screen. Draw it as the FIRST child of the screen
 * Box so cards sit on top of it.
 *
 * listening / player / settings : accent, top, ~420dp tall
 * listening (second)            : sage at .22, bottom-left, 340dp
 * auxshare                      : sage at .30 top, accent bottom-right 320dp
 * sheet                         : accent, top, 480dp
 */
@Composable
fun ScreenBloom(
    color: Color,
    alpha: Float,
    modifier: Modifier = Modifier
) {
    val c = glass()
    Box(
        modifier = modifier.background(
            androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(color.copy(alpha = alpha), Color.Transparent)
            )
        )
    )
}
