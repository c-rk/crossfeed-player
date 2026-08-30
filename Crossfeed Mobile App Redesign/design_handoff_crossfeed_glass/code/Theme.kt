/*
 * Crossfeed — Glass theme tokens.
 *
 * Design reference, not final code: match your repo's package, and if it already
 * has a MaterialTheme wrapper, feed these values into its ColorScheme instead of
 * introducing a parallel theme.
 *
 * The one non-obvious idea: the accent is NOT a theme constant. It is derived
 * from routes[0] — the primary music service. Everything else is fixed per mode.
 * `sage` is fixed across all services: it always means "other people".
 */

package org.tentkotta.crossfeed.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class GlassColors(
    val bg: Color,
    val t1: Color,
    val t2: Color,
    val t3: Color,
    val line: Color,
    val dot: Color,
    val p0: Color,
    val p1: Color,
    val p2: Color,
    val p3: Color,
    val bd: Color,
    val hi: Color,
    val off: Color,
    val scrim: Color,
    val sage: Color,
    val sageTint: Color,
    val sageBorder: Color,
    /** Alpha applied to the accent when it is painted as a background bloom. */
    val bloomAlpha: Float,
    /** Derived from the primary route — see Routing.kt. */
    val accent: Color,
    /** Text/icon colour that sits ON the accent. Always the dark ground. */
    val onAccent: Color = Color(0xFF0B0B0C)
)

private val DarkBase = GlassColors(
    bg = Color(0xFF0B0B0C),
    t1 = Color(0xFFF7F5F2),
    t2 = Color(0xFFF7F5F2).copy(alpha = 0.88f),
    t3 = Color(0xFFF7F5F2).copy(alpha = 0.76f),
    line = Color(0xFFF7F5F2).copy(alpha = 0.30f),
    dot = Color(0xFFF7F5F2).copy(alpha = 0.50f),
    p0 = Color.White.copy(alpha = 0.05f),
    p1 = Color.White.copy(alpha = 0.07f),
    p2 = Color.White.copy(alpha = 0.09f),
    p3 = Color(0xFF1C1C1F),
    bd = Color.White.copy(alpha = 0.14f),
    hi = Color.White.copy(alpha = 0.14f),
    off = Color.White.copy(alpha = 0.22f),
    scrim = Color(0xFF0B0B0C).copy(alpha = 0.60f),
    sage = Color(0xFFAEBF92),
    sageTint = Color(0xFFAEBF92).copy(alpha = 0.18f),
    sageBorder = Color(0xFFAEBF92).copy(alpha = 0.28f),
    bloomAlpha = 0.34f,
    accent = Service.AppleMusic.accent
)

private val LightBase = GlassColors(
    bg = Color(0xFFF4F2EE),
    t1 = Color(0xFF141416),
    t2 = Color(0xFF141416).copy(alpha = 0.82f),
    t3 = Color(0xFF141416).copy(alpha = 0.66f),
    line = Color(0xFF141416).copy(alpha = 0.20f),
    dot = Color(0xFF141416).copy(alpha = 0.34f),
    p0 = Color.White.copy(alpha = 0.50f),
    p1 = Color.White.copy(alpha = 0.66f),
    p2 = Color.White.copy(alpha = 0.80f),
    p3 = Color(0xFFE2DDD5),
    bd = Color(0xFF141416).copy(alpha = 0.14f),
    hi = Color.White.copy(alpha = 0.90f),
    off = Color(0xFF141416).copy(alpha = 0.20f),
    scrim = Color(0xFFF4F2EE).copy(alpha = 0.78f),
    // Sage darkens in light mode so it stays legible on cream.
    sage = Color(0xFF59703C),
    sageTint = Color(0xFF7A8A5E).copy(alpha = 0.20f),
    sageBorder = Color(0xFF7A8A5E).copy(alpha = 0.34f),
    bloomAlpha = 0.20f,
    accent = Service.AppleMusic.accent
)

fun glassColors(dark: Boolean, accent: Color): GlassColors =
    (if (dark) DarkBase else LightBase).copy(accent = accent)

/** Corner radii. Every pill in the design is fully rounded — use [Pill]. */
object Radius {
    val screen: Dp = 30.dp
    val card: Dp = 22.dp
    val transport: Dp = 24.dp
    val gridCard: Dp = 20.dp
    val artLarge: Dp = 16.dp
    val artMedium: Dp = 14.dp
    val artSmall: Dp = 12.dp
    val artTiny: Dp = 9.dp
    val pill: Dp = 999.dp
}

/** Layout constants that recur across screens. */
object Space {
    val screenH: Dp = 18.dp
    val screenHWide: Dp = 22.dp
    val cardPadV: Dp = 12.dp
    val cardPadH: Dp = 13.dp
    val betweenCards: Dp = 9.dp
    val listRowGap: Dp = 11.dp
}

val LocalGlassColors: ProvidableCompositionLocal<GlassColors> =
    staticCompositionLocalOf { DarkBase }

/**
 * Wrap the app once. `accent` should come from the primary route so a change to
 * routing retints every screen with no other plumbing.
 */
@Composable
fun CrossfeedTheme(
    dark: Boolean,
    accent: Color,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalGlassColors provides glassColors(dark, accent), content = content)
}

/** Shorthand: `val c = glass()` inside any composable. */
@Composable
fun glass(): GlassColors = LocalGlassColors.current
