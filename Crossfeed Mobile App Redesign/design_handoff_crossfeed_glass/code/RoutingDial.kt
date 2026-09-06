/*
 * Crossfeed — the radial routing dial.
 *
 * 250dp circle. Six 52dp service nodes on an 88dp radius, first at 12 o'clock,
 * stepping clockwise by 60°. 12 tick marks. A 118dp hub naming the primary.
 *
 * Tap a node   -> add or drop that service (never below one)
 * Tap a chip   -> promote to primary (retints the app)
 */

package org.tentkotta.crossfeed.ui.routing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import org.tentkotta.crossfeed.routing.Routes
import org.tentkotta.crossfeed.routing.Service
import org.tentkotta.crossfeed.ui.theme.glass

private val DIAL = 250.dp
private val NODE = 52.dp
private val ORBIT = 88.dp
private val HUB = 118.dp

@Composable
fun RoutingDial(
    routes: Routes,
    onToggle: (Service) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = glass()
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .size(DIAL)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(c.p2, c.p0),
                    center = Offset.Unspecified,
                    radius = with(density) { (DIAL / 2).toPx() }
                )
            )
            .border(1.dp, c.bd, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        DialTicks(color = c.line)

        // Hub — names the primary route and how many fallbacks sit behind it.
        Column(
            modifier = Modifier
                .size(HUB)
                .clip(CircleShape)
                .background(c.p2)
                .border(1.dp, c.bd, CircleShape)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AppText(
                text = routes.primary.label,
                size = 13.sp,
                weight = FontWeight.Bold,
                color = c.t1,
                align = TextAlign.Center
            )
            Spacer(Modifier.height(5.dp))
            AppText(
                text = routes.summary,
                size = 9.5.sp,
                weight = FontWeight.Normal,
                color = c.t3,
                align = TextAlign.Center
            )
        }

        Service.dialOrder.forEachIndexed { index, service ->
            val angle = (index.toFloat() / Service.dialOrder.size) * (2 * Math.PI) - Math.PI / 2
            val dx = (cos(angle) * with(density) { ORBIT.toPx() }).roundToInt()
            val dy = (sin(angle) * with(density) { ORBIT.toPx() }).roundToInt()
            val rank = routes.rankOf(service)

            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(dx, dy)
                        }
                    }
            ) {
                ServiceNode(
                    service = service,
                    rank = rank,
                    onClick = { onToggle(service) }
                )
            }
        }
    }
}

@Composable
private fun ServiceNode(
    service: Service,
    rank: Int?,
    onClick: () -> Unit
) {
    val c = glass()
    val selected = rank != null

    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            modifier = Modifier
                .size(NODE)
                .clip(CircleShape)
                .then(
                    if (selected) {
                        Modifier
                            // The 3dp halo: service accent at 28%.
                            .border(3.dp, service.accent.copy(alpha = 0.28f), CircleShape)
                            .background(service.accent)
                    } else {
                        Modifier
                            .background(c.p1)
                            .border(1.dp, c.bd, CircleShape)
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            // Replace with the real brand mark — see README > Assets.
            ServiceMark(
                service = service,
                tint = if (selected) c.onAccent else c.t3
            )
        }

        if (rank != null) {
            Box(
                modifier = Modifier
                    .size(17.dp)
                    .offset(x = 2.dp, y = (-2).dp)
                    .clip(CircleShape)
                    .background(c.bg),
                contentAlignment = Alignment.Center
            ) {
                AppText(
                    text = "${rank + 1}",
                    size = 9.sp,
                    weight = FontWeight.Bold,
                    color = service.accent
                )
            }
        }
    }
}

/** 12 marks; every third is long. Origin is the dial centre. */
@Composable
private fun DialTicks(color: Color) {
    Canvas(modifier = Modifier.size(DIAL)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = size.width / 2f - 6.dp.toPx()
        repeat(12) { i ->
            val long = i % 3 == 0
            val len = (if (long) 9.dp else 5.dp).toPx()
            val a = (i * 30f) * (Math.PI / 180f) - Math.PI / 2
            val sx = cx + cos(a).toFloat() * outer
            val sy = cy + sin(a).toFloat() * outer
            val ex = cx + cos(a).toFloat() * (outer - len)
            val ey = cy + sin(a).toFloat() * (outer - len)
            drawLine(
                color = color.copy(alpha = color.alpha * 0.55f),
                start = Offset(sx, sy),
                end = Offset(ex, ey),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

/**
 * Rank chips under the dial: "1 · apple music", "2 · spotify".
 * Tapping one promotes it.
 */
@Composable
fun RouteRankChips(
    routes: Routes,
    onPromote: (Service) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = glass()
    FlowRowCompat(modifier = modifier, gap = 5.dp) {
        routes.ordered.forEachIndexed { index, service ->
            val isPrimary = index == 0
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .then(
                        if (isPrimary) Modifier.background(c.accent)
                        else Modifier.background(c.p1).border(1.dp, c.bd, CircleShape)
                    )
                    .clickable { onPromote(service) }
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            ) {
                AppText(
                    text = "${index + 1} · ${service.label}",
                    size = 9.5.sp,
                    weight = FontWeight.Bold,
                    color = if (isPrimary) c.onAccent else c.t3
                )
            }
        }
    }
}

/*
 * Stubs to wire to your own code:
 *  - AppText(...)      your Figtree text style wrapper
 *  - ServiceMark(...)  the real brand logo per service
 *  - FlowRowCompat     androidx.compose.foundation.layout.FlowRow, or your own
 */
