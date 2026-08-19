package dev.crossfeed.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.crossfeed.ui.settings.SettingsScreen
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type

@Composable
fun CrossfeedApp() {
    val nav = rememberNav()

    BackHandler(enabled = nav.canPop) { nav.pop() }

    Backdrop {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Crossfade(targetState = nav.current, label = "dest") { dest ->
                when (dest) {
                    Dest.HOME -> ListeningScreen()
                    Dest.AUX -> SocialScreen()
                    Dest.PLAYER -> PlayerScreen()
                    Dest.SETTINGS -> SettingsScreen(nav)
                    Dest.ROUTE -> Sub("route", nav::pop) { RouteScreen() }
                }
            }
        }
        PlayerSheet {
            TabBar(
                selected = nav.root,
                onSelect = nav::select,
                modifier = Modifier.padding(bottom = Space.small),
            )
        }
    }
}

@Composable
fun Sub(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    val glass = LocalGlass.current
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.large, vertical = Space.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onBack)
                    .padding(horizontal = Space.small, vertical = 4.dp),
            ) {
                Text("back", style = Type.caps, color = glass.inkMuted)
            }
            Spacer(Modifier.padding(horizontal = Space.tight))
            Text(title, style = Type.blockTitle, color = glass.ink)
        }
        content()
    }
}

@Composable
private fun TabBar(selected: Dest, onSelect: (Dest) -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(glass.chrome)
            .background(Brush.verticalGradient(listOf(glass.fill, Color.Transparent)))
            .border(1.dp, glass.stroke, shape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (dest in Roots) {
            Tab(dest, selected == dest) { onSelect(dest) }
        }
    }
}

@Composable
private fun Tab(dest: Dest, active: Boolean, onClick: () -> Unit) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .then(
                if (active) {
                    Modifier.background(Brush.linearGradient(glass.hot))
                } else {
                    Modifier.background(Color.Transparent)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = if (dest == Dest.SETTINGS) 12.dp else 14.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (dest == Dest.SETTINGS) {
            Gear(active = active)
        } else {
            Text(
                dest.label,
                style = Type.callout,
                color = if (active) Color.White else glass.ink,
            )
        }
    }
}
