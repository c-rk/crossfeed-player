package dev.crossfeed.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.crossfeed.core.Platform
import dev.crossfeed.core.Prefs

/**
 * The colours, as tokens.
 *
 * One idea runs through all of it: the accent is not a property of the app, it is a property of
 * wherever the listener's music comes from. Route through spotify and the whole thing goes green.
 * Everything else is fixed per mode.
 *
 * Sage is the exception that proves it. Sage always means other people, whatever the accent is
 * doing: friends listening, reactions arriving, a song sent back. It is never tinted.
 */
@Immutable
data class Glass(
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
    /** How strongly the accent is allowed to bloom behind a screen. */
    val bloomAlpha: Float,
    /** Inherited from the lead route. */
    val accent: Color,
    val warning: Color,
    val dark: Boolean,
) {
    /** What sits on top of an accent fill. Always the dark ground, in both modes. */
    val onAccent: Color get() = Color(0xFF0B0B0C)

    /** What sits on top of a sage fill. */
    val onSage: Color get() = Color(0xFF141410)

    // the names the rest of the app already asks for, pointed at the tokens above so a screen
    // that has not been rebuilt yet still comes out in the new palette

    val ink: Color get() = t1
    val inkMuted: Color get() = t2
    val inkFaint: Color get() = t3
    val fill: Color get() = p1
    val fillStrong: Color get() = p2
    val stroke: Color get() = bd
    val strokeSoft: Color get() = bd
    val tile: Color get() = p3
    val deep: Color get() = bg
    val chrome: Color get() = bg
    val positive: Color get() = sage
    val sheet: Color get() = if (dark) Color(0xFF141416) else Color(0xFFEFEDE8)
    val hot: List<Color> get() = listOf(accent, accent)
    val cool: List<Color> get() = listOf(sage, sage)
    val wash: List<Color> get() = listOf(bg, bg, bg)
}

private val Night = Glass(
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
    accent = Color(0xFFFF375F),
    warning = Color(0xFFFF6B5A),
    dark = true,
)

private val Day = Glass(
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
    sage = Color(0xFF59703C),
    sageTint = Color(0xFF7A8A5E).copy(alpha = 0.20f),
    sageBorder = Color(0xFF7A8A5E).copy(alpha = 0.34f),
    bloomAlpha = 0.20f,
    accent = Color(0xFFFF375F),
    warning = Color(0xFFD62E17),
    dark = false,
)

/**
 * Each service's own colour, and one for the listener's own files.
 *
 * These are the brand colours, not approximations of them, because the whole point is that the app
 * looks like it belongs to wherever the music is coming from.
 */
object Accents {

    val onDevice = Color(0xFFF6A06B)

    fun of(routeId: String?): Color = when (routeId) {
        Platform.APPLE_MUSIC.id -> Color(0xFFFF375F)
        Platform.SPOTIFY.id -> Color(0xFF1ED760)
        Platform.YOUTUBE_MUSIC.id -> Color(0xFFFF5A3C)
        Platform.DEEZER.id -> Color(0xFFA45CFF)
        Platform.TIDAL.id -> Color(0xFF3AD0FF)
        else -> onDevice
    }

    fun nameOf(routeId: String?): String =
        Platform.byId(routeId)?.label ?: "on device"
}

object Shapes {
    /** The standard glass card. */
    val card = RoundedCornerShape(22.dp)
    val cardMirror = RoundedCornerShape(22.dp)

    /** A card in a feed grid. */
    val grid = RoundedCornerShape(20.dp)

    /** Album art, at the three sizes it appears in. */
    val art = RoundedCornerShape(16.dp)
    val artSmall = RoundedCornerShape(14.dp)
    val artRow = RoundedCornerShape(12.dp)

    val tile = RoundedCornerShape(18.dp)
    val field = RoundedCornerShape(50)
    val button = RoundedCornerShape(50)
    val chip = RoundedCornerShape(50)
    val sheet = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
}

object Space {
    val hair: Dp = 1.dp
    val tight: Dp = 6.dp
    val small: Dp = 9.dp
    val medium: Dp = 14.dp
    val large: Dp = 18.dp
    val section: Dp = 24.dp
    val corner: Dp = 22.dp
    val cornerSmall: Dp = 14.dp
}

val LocalGlass = staticCompositionLocalOf { Night }

/**
 * Which colour mode is showing, and which route is painting it.
 *
 * Both are read from preferences once and then held here, so a tap on the dial retints every
 * screen at once rather than waiting for something to be reopened.
 */
object Look {
    /** null follows the phone. */
    var mode by mutableStateOf<String?>(null)
        private set

    var lead by mutableStateOf<String?>(null)
        private set

    fun load(context: Context) {
        val prefs = Prefs(context)
        mode = prefs.mode
        lead = prefs.leadRoute
    }

    fun setMode(context: Context, value: String?) {
        Prefs(context).mode = value
        mode = value
    }

    fun routed(context: Context) {
        lead = Prefs(context).leadRoute
    }
}

/** Kept so older screens that seeded the theme from a package still compile. */
object ThemeSeed {
    var pkg by mutableStateOf<String?>(null)
}

@Composable
fun CrossfeedTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    remember { Look.load(context); 0 }

    val night = when (Look.mode) {
        "dark" -> true
        "light" -> false
        else -> dark
    }
    val glass = (if (night) Night else Day).copy(accent = Accents.of(Look.lead))

    val scheme = if (night) {
        darkColorScheme(
            background = glass.bg,
            onBackground = glass.t1,
            surface = glass.bg,
            onSurface = glass.t1,
            primary = glass.accent,
            outline = glass.bd,
            error = glass.warning,
        )
    } else {
        lightColorScheme(
            background = glass.bg,
            onBackground = glass.t1,
            surface = glass.bg,
            onSurface = glass.t1,
            primary = glass.accent,
            outline = glass.bd,
            error = glass.warning,
        )
    }
    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(colorScheme = scheme, typography = CrossfeedTypography, content = content)
    }
}
