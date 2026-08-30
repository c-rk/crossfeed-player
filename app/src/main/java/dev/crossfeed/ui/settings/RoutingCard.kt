package dev.crossfeed.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.crossfeed.core.Platform
import dev.crossfeed.core.Prefs
import dev.crossfeed.ui.Glyph
import dev.crossfeed.ui.GlassCard
import dev.crossfeed.ui.Mark
import dev.crossfeed.ui.PlatformGlyph
import dev.crossfeed.ui.theme.Accents
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Look
import dev.crossfeed.ui.theme.Shapes
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val DIAL = 250.dp
private val NODE = 52.dp
private val ORBIT = 88.dp
private val HUB = 118.dp

/** The order the six sit in around the face, starting at twelve and going clockwise. */
private val dialOrder = listOf(
    Platform.APPLE_MUSIC.id,
    Platform.SPOTIFY.id,
    Platform.YOUTUBE_MUSIC.id,
    Platform.DEEZER.id,
    Platform.TIDAL.id,
    Prefs.ON_DEVICE,
)

/**
 * Where music comes from, as a dial rather than a list of checkboxes.
 *
 * The order is the whole point. The first route is the shortcut a link takes and the colour the
 * app wears; the rest are what a chooser offers under it. Tapping a logo adds or drops it, tapping
 * a rank chip promotes it, and the moment the first one changes the entire app changes colour.
 */
@Composable
fun RoutingCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val prefs = remember { Prefs(context) }
    var routes by remember { mutableStateOf(prefs.routes) }

    fun commit(next: List<String>) {
        val clean = next.distinct().ifEmpty { routes }
        prefs.routes = clean
        routes = prefs.routes
        Look.routed(context)
    }

    GlassCard(padding = 13.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "WHERE MUSIC COMES FROM",
                style = Type.tagWide,
                color = glass.accent,
                modifier = Modifier.weight(1f),
            )
            ModeToggle()
        }

        Spacer(Modifier.height(Space.medium))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Dial(routes) { id ->
                val has = id in routes
                // never nothing: a route list with no routes has nowhere to send anything
                if (has && routes.size == 1) return@Dial
                commit(if (has) routes - id else routes + id)
            }
        }

        Spacer(Modifier.height(Space.medium))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Space.tight),
        ) {
            routes.forEachIndexed { index, id ->
                RankChip(index + 1, id, first = index == 0) {
                    commit(listOf(id) + routes.filterNot { it == id })
                }
            }
        }

        Spacer(Modifier.height(Space.small))
        Text(
            "tap a logo to add it or drop it · tap a chip to make it first. one is a shortcut, " +
                "several is a chooser.",
            style = Type.description,
            color = glass.t3,
        )
    }
}

@Composable
private fun Dial(routes: List<String>, onToggle: (String) -> Unit) {
    val glass = LocalGlass.current
    val density = LocalDensity.current

    Box(
        Modifier
            .size(DIAL)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(glass.p2, glass.p0)))
            .border(1.dp, glass.bd, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Ticks()

        Column(
            Modifier
                .size(HUB)
                .clip(CircleShape)
                .background(glass.p2)
                .border(1.dp, glass.bd, CircleShape)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                Accents.nameOf(routes.firstOrNull()),
                style = Type.rowTitleLarge,
                color = glass.t1,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                if (routes.size <= 1) "the only route" else "+ ${routes.size - 1} more, in order",
                style = Type.meta,
                color = glass.t3,
                textAlign = TextAlign.Center,
            )
        }

        dialOrder.forEachIndexed { index, id ->
            val angle = index * (Math.PI / 3) - Math.PI / 2
            val orbit = with(density) { ORBIT.toPx() }
            Box(
                Modifier.offset {
                    IntOffset((cos(angle) * orbit).roundToInt(), (sin(angle) * orbit).roundToInt())
                },
            ) {
                Node(id, rank = routes.indexOf(id).takeIf { it >= 0 }?.plus(1)) { onToggle(id) }
            }
        }
    }
}

/** Twelve marks around the rim, every third one longer, the way a dial is marked. */
@Composable
private fun Ticks() {
    val glass = LocalGlass.current
    Canvas(Modifier.size(DIAL)) {
        val centre = Offset(size.width / 2f, size.height / 2f)
        val outer = size.minDimension / 2f - 6f
        repeat(12) { index ->
            val angle = Math.toRadians(index * 30.0 - 90.0)
            val length = if (index % 3 == 0) 9.dp.toPx() else 5.dp.toPx()
            val from = Offset(
                centre.x + (cos(angle) * outer).toFloat(),
                centre.y + (sin(angle) * outer).toFloat(),
            )
            val to = Offset(
                centre.x + (cos(angle) * (outer - length)).toFloat(),
                centre.y + (sin(angle) * (outer - length)).toFloat(),
            )
            drawLine(glass.line.copy(alpha = glass.line.alpha * 0.55f), from, to, strokeWidth = 1.dp.toPx())
        }
    }
}

@Composable
private fun Node(id: String, rank: Int?, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val brand = Accents.of(id)
    val on = rank != null

    Box(Modifier.size(NODE + 8.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(NODE)
                .clip(CircleShape)
                .background(if (on) brand else glass.p1)
                .border(1.dp, if (on) brand.copy(alpha = 0.28f) else glass.bd, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val platform = Platform.byId(id)
            if (platform == null) {
                Mark(Glyph.DEVICE, side = 22.dp, tint = if (on) glass.onAccent else glass.t3)
            } else {
                PlatformGlyph(
                    platform,
                    size = 24.dp,
                    drawn = true,
                    tint = if (on) glass.onAccent else glass.t3,
                )
            }
        }
        if (rank != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(17.dp)
                    .clip(CircleShape)
                    .background(glass.bg),
                contentAlignment = Alignment.Center,
            ) {
                Text("$rank", style = Type.tagSmall, color = brand)
            }
        }
    }
}

@Composable
private fun RankChip(rank: Int, id: String, first: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Box(
        Modifier
            .clip(Shapes.chip)
            .background(if (first) glass.accent else glass.p1)
            .border(1.dp, if (first) Color.Transparent else glass.bd, Shapes.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    ) {
        Text(
            "$rank · " + Accents.nameOf(id),
            style = Type.chip,
            color = if (first) glass.onAccent else glass.t3,
            maxLines = 1,
        )
    }
}

/** Dark or light, chosen rather than inherited, once the listener has an opinion. */
@Composable
private fun ModeToggle() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    Row(
        Modifier
            .clip(Shapes.chip)
            .background(glass.p1)
            .border(1.dp, glass.bd, Shapes.chip)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (option in listOf("dark", "light")) {
            val on = Look.mode == option || (Look.mode == null && glass.dark == (option == "dark"))
            Box(
                Modifier
                    .clip(Shapes.chip)
                    .background(if (on) glass.t1 else Color.Transparent)
                    .clickable { Look.setMode(context, option) }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Text(option, style = Type.tagSmall, color = if (on) glass.bg else glass.t3)
            }
        }
    }
}
