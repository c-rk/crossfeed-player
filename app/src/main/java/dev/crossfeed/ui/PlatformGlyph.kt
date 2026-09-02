package dev.crossfeed.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.crossfeed.R
import dev.crossfeed.core.Platform

/**
 * A service's face, wherever one is needed.
 *
 * The installed app's own icon comes first, since that is the icon the listener already knows from
 * their home screen. When the app is not on the phone there is a drawn mark to fall back on, which
 * matters for the services crossfeed can link to without being installed: a row that reads only
 * "T" or "D" is a row nobody recognises at a glance.
 */
@Composable
fun PlatformGlyph(
    platform: Platform,
    size: Dp,
    dimmed: Boolean = false,
    /** Forces the drawn mark, for places where the installed icon would be the wrong shape. */
    drawn: Boolean = false,
    /** Overrides the mark's colour, for when it sits on a filled node rather than a dark one. */
    tint: Color? = null,
) {
    val icon = if (drawn) null else rememberAppIcon(platform.pkg)
    val faded = Modifier.alpha(if (dimmed) 0.4f else 1f)

    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = platform.label,
            modifier = faded.then(Modifier.size(size).clip(CircleShape)),
        )
        return
    }
    // an app icon, not a symbol: the brand's own colour as the disc, the mark in white on top,
    // so a service you have not installed still reads at a glance beside ones you have
    Box(
        faded.then(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(tint ?: fillOf(platform)),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(markOf(platform)),
            contentDescription = platform.label,
            colorFilter = ColorFilter.tint(inkOf(platform)),
            modifier = Modifier.size(size * 0.62f),
        )
    }
}

private fun markOf(platform: Platform): Int = when (platform) {
    Platform.APPLE_MUSIC -> R.drawable.ic_service_apple
    Platform.SPOTIFY -> R.drawable.ic_service_spotify
    Platform.YOUTUBE_MUSIC -> R.drawable.ic_service_youtube
    Platform.TIDAL -> R.drawable.ic_service_tidal
    Platform.DEEZER -> R.drawable.ic_service_deezer
}

/** The disc behind the mark: each service's own colour. */
private fun fillOf(platform: Platform): Color = when (platform) {
    Platform.APPLE_MUSIC -> Color(0xFFFA243C)
    Platform.SPOTIFY -> Color(0xFF1DB954)
    Platform.YOUTUBE_MUSIC -> Color(0xFFFF0033)
    Platform.DEEZER -> Color(0xFFA238FF)
    Platform.TIDAL -> Color(0xFF00FFFF)
}

/** And what sits on it. Tidal's mark is black on its cyan; everyone else's is white. */
private fun inkOf(platform: Platform): Color =
    if (platform == Platform.TIDAL) Color(0xFF0B0B0C) else Color.White
