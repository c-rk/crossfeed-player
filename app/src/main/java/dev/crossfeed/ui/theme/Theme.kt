package dev.crossfeed.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Glass(
    val fill: Color,
    val fillStrong: Color,
    val tile: Color,
    val sheet: Color,
    val deep: Color,
    val stroke: Color,
    val strokeSoft: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val accent: Color,
    val hot: List<Color>,
    val cool: List<Color>,
    val positive: Color,
    val warning: Color,
    val wash: List<Color>,
    val chrome: Color,
    val dark: Boolean,
)

private val Night = Glass(
    fill = Color(0x38000000),
    fillStrong = Color(0x4D000000),
    tile = Color(0xE6000000),
    sheet = Color(0xF21A1620),
    deep = Color(0xFF070709),
    stroke = Color(0x59FFFFFF),
    strokeSoft = Color(0x2EFFFFFF),
    ink = Color(0xFFFFFFFF),
    inkMuted = Color(0xD9FFFFFF),
    inkFaint = Color(0x8CFFFFFF),
    accent = Color(0xFF8B5CFF),
    hot = listOf(Color(0xFF9B4DFF), Color(0xFFFF3D8B)),
    cool = listOf(Color(0xFF00D4FF), Color(0xFF6A5BFF)),
    positive = Color(0xFF3DE8A8),
    warning = Color(0xFFFF6B5A),
    wash = listOf(Color(0xFF141418), Color(0xFF0D0D10), Color(0xFF060608)),
    chrome = Color(0xF2140F26),
    dark = true,
)

private val Day = Glass(
    fill = Color(0x59FFFFFF),
    fillStrong = Color(0x8CFFFFFF),
    tile = Color(0xE60B0B0F),
    sheet = Color(0xF7FAFAFC),
    deep = Color(0xFF0A0A0C),
    stroke = Color(0x99FFFFFF),
    strokeSoft = Color(0x4DFFFFFF),
    ink = Color(0xFF000000),
    inkMuted = Color(0xD9000000),
    inkFaint = Color(0x8C000000),
    accent = Color(0xFF6B2BFF),
    hot = listOf(Color(0xFF7B2BFF), Color(0xFFFF2E7E)),
    cool = listOf(Color(0xFF00B8E6), Color(0xFF5B4BFF)),
    positive = Color(0xFF00996B),
    warning = Color(0xFFD62E17),
    wash = listOf(Color(0xFFF4F4F6), Color(0xFFEDEDF0), Color(0xFFFAFAFB)),
    chrome = Color(0xF2FFFFFF),
    dark = false,
)

object Shapes {
    val card = RoundedCornerShape(28.dp)
    val cardMirror = RoundedCornerShape(28.dp)
    val tile = RoundedCornerShape(22.dp)
    val field = RoundedCornerShape(50)
    val button = RoundedCornerShape(50)
    val chip = RoundedCornerShape(50)
    val sheet = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
}

object Space {
    val hair: Dp = 1.dp
    val tight: Dp = 6.dp
    val small: Dp = 10.dp
    val medium: Dp = 16.dp
    val large: Dp = 22.dp
    val section: Dp = 30.dp
    val corner: Dp = 26.dp
    val cornerSmall: Dp = 16.dp
}

val LocalGlass = staticCompositionLocalOf { Night }

object ThemeSeed {
    var pkg by mutableStateOf<String?>(null)
}

@Composable
fun CrossfeedTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val daypart = remember { currentDaypart() }
    val seedPkg = ThemeSeed.pkg
    val seed = remember(seedPkg) { seedPkg?.let { appAccent(context, it) } }
    val mood = remember(daypart, seed) { moodFor(daypart, seed) }
    val base = if (dark) Night else Day
    val glass = base.copy(
        accent = mood.accent,
        hot = mood.hot,
        cool = mood.cool,
        wash = if (dark) mood.wash else base.wash,
    )
    val scheme = if (dark) {
        darkColorScheme(
            background = Color.Transparent,
            onBackground = glass.ink,
            surface = Color.Transparent,
            onSurface = glass.ink,
            primary = glass.accent,
            outline = glass.stroke,
            error = glass.warning,
        )
    } else {
        lightColorScheme(
            background = Color.Transparent,
            onBackground = glass.ink,
            surface = Color.Transparent,
            onSurface = glass.ink,
            primary = glass.accent,
            outline = glass.stroke,
            error = glass.warning,
        )
    }
    CompositionLocalProvider(LocalGlass provides glass) {
        MaterialTheme(colorScheme = scheme, typography = CrossfeedTypography, content = content)
    }
}
