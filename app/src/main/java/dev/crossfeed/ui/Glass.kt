package dev.crossfeed.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type

/**
 * The panel recipe, used for every card in the app: a barely there fill, a hairline border, and a
 * single line of light along the top edge where a shadow would otherwise go. Nothing in this
 * design casts a shadow, so depth has to come from the light instead.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.card,
    strong: Boolean = false,
    padding: Dp = Space.medium,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlass.current
    Column(
        modifier
            .clip(shape)
            .background(if (strong) glass.sheet else glass.p1)
            .highlight(shape)
            .border(BorderStroke(1.dp, glass.bd), shape)
            .padding(padding),
        content = content,
    )
}

/** The same panel, in sage, for anything that came from another person. */
@Composable
fun SageCard(
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.card,
    padding: Dp = Space.medium,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glass = LocalGlass.current
    Column(
        modifier
            .clip(shape)
            .background(glass.sageTint)
            .border(BorderStroke(1.dp, glass.sageBorder), shape)
            .padding(padding),
        content = content,
    )
}

/** One pixel of light along the top edge, standing in for the inset highlight. */
@Composable
private fun Modifier.highlight(shape: Shape): Modifier {
    val glass = LocalGlass.current
    return drawBehind {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(glass.hi, Color.Transparent),
                startY = 0f,
                endY = 2f,
            ),
            size = Size(size.width, 2f),
        )
    }
}

/**
 * The soft light behind a screen, in whatever colour the lead route is wearing. One at the top
 * always, and on some pages a second, in sage, low and to one side, because the app's other half
 * is other people.
 */
@Composable
fun Bloom(
    modifier: Modifier = Modifier,
    top: Color? = null,
    topAlpha: Float? = null,
    bottom: Color? = null,
    bottomAlpha: Float = 0.22f,
    bottomLeft: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlass.current
    val head = top ?: glass.accent
    val headAlpha = topAlpha ?: glass.bloomAlpha

    Box(
        modifier
            .fillMaxSize()
            .background(glass.bg)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(head.copy(alpha = headAlpha), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.02f),
                        radius = size.width * 0.95f,
                    ),
                )
                if (bottom != null) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(bottom.copy(alpha = bottomAlpha), Color.Transparent),
                            center = Offset(
                                if (bottomLeft) size.width * 0.06f else size.width * 0.94f,
                                size.height * 0.86f,
                            ),
                            radius = size.width * 0.8f,
                        ),
                    )
                }
            },
        content = content,
    )
}

/** Kept for screens that have not been rebuilt yet. */
@Composable
fun Backdrop(content: @Composable () -> Unit) {
    Bloom(bottom = LocalGlass.current.sage) { content() }
}

@Composable
fun GlassButton(
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    enabled: Boolean = true,
    compact: Boolean = false,
    sage: Boolean = false,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    val shape = Shapes.button
    val background = when {
        !enabled -> Color.Transparent
        sage -> glass.sageTint
        filled -> glass.accent
        else -> glass.p2
    }
    val ink = when {
        !enabled -> glass.t3
        sage -> glass.sage
        filled -> glass.onAccent
        else -> glass.t2
    }
    Box(
        modifier
            .clip(shape)
            .background(background)
            .border(
                BorderStroke(
                    1.dp,
                    when {
                        !enabled -> glass.bd
                        sage -> glass.sageBorder
                        filled -> Color.Transparent
                        else -> glass.bd
                    },
                ),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) Space.small + 2.dp else Space.large,
                vertical = if (compact) 7.dp else 12.dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = if (compact) Type.chip else Type.label, color = ink, maxLines = 1)
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
            .background(if (selected) glass.accent else glass.p0)
            .border(BorderStroke(1.dp, if (selected) glass.accent else glass.bd), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 13.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = Type.chip,
            color = if (selected) glass.onAccent else glass.t3,
            maxLines = 1,
        )
    }
}

/** A round accent button, the one shape the app uses for "play this". */
@Composable
fun PlayCircle(
    diameter: Dp,
    playing: Boolean = false,
    sage: Boolean = false,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(if (sage) glass.sage else glass.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Mark(
            if (playing) Glyph.PAUSE else Glyph.PLAY,
            side = diameter * 0.42f,
            tint = if (sage) glass.onSage else glass.onAccent,
        )
    }
}

/** A quieter round button, for the things either side of the play one. */
@Composable
fun SoftCircle(
    glyph: Glyph,
    diameter: Dp,
    onClick: () -> Unit,
) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(glass.p2)
            .border(BorderStroke(1.dp, glass.bd), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Mark(glyph, side = diameter * 0.42f, tint = glass.t2)
    }
}

/**
 * The one entrance in the app. Anything that opens in place rather than as a screen rises a few
 * pixels while it fades in, and nothing bounces.
 */
@Composable
fun Rise(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)) + slideInVertically(tween(200)) { 6 },
        exit = fadeOut(tween(140)) + slideOutVertically(tween(140)) { 6 },
    ) {
        content()
    }
}

/** The list and grid pair that sits at the right of a section heading. */
@Composable
fun ViewToggle(grid: Boolean, onChange: (Boolean) -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .clip(Shapes.chip)
            .background(glass.p1)
            .border(BorderStroke(1.dp, glass.bd), Shapes.chip)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (wantsGrid in listOf(false, true)) {
            val on = wantsGrid == grid
            Box(
                Modifier
                    .size(width = 26.dp, height = 21.dp)
                    .clip(Shapes.chip)
                    .background(if (on) glass.accent else Color.Transparent)
                    .clickable { onChange(wantsGrid) },
                contentAlignment = Alignment.Center,
            ) {
                Mark(
                    if (wantsGrid) Glyph.GRID else Glyph.LIST,
                    side = 11.dp,
                    tint = if (on) glass.onAccent else glass.t3,
                )
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, trailing: (@Composable () -> Unit)? = null) {
    val glass = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().padding(bottom = Space.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Type.section, color = glass.t1, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A hairline between rows in a list. */
@Composable
fun RowRule() {
    val glass = LocalGlass.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(glass.p2))
}

/**
 * How much room to leave at the foot of a page.
 *
 * The mini player and the nav pill float over the page rather than pushing it up, which is what
 * lets a feed carry on underneath them. The cost of that is every scrolling page has to know they
 * are there, so it is worked out once, here, and includes the phone's own gesture bar.
 */
@Composable
fun bottomRoom(): Dp {
    val gesture = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // the nav pill, and the mini player when there is something playing to show in it
    val furniture = if (rememberDeck() != null) 152.dp else 68.dp
    return gesture + furniture
}

@Composable
fun tapless() = remember { MutableInteractionSource() }
