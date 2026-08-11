package dev.crossfeed.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type

@Composable
fun CrossfeedApp() {
    var tab by rememberSaveable { mutableStateOf(0) }

    Backdrop {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Crossfade(targetState = tab, label = "tab") { current ->
                when (current) {
                    0 -> RouteScreen()
                    1 -> PlayerScreen()
                    2 -> ListeningScreen()
                    else -> SocialScreen()
                }
            }
        }
        PlayerSheet {
            TabBar(
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(bottom = Space.small),
            )
        }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(glass.chrome)
            .background(Brush.verticalGradient(listOf(glass.fill, Color.Transparent)))
            .border(1.dp, glass.stroke, shape)
            .padding(4.dp),
    ) {
        Tab("route", selected == 0) { onSelect(0) }
        Tab("player", selected == 1) { onSelect(1) }
        Tab("listening", selected == 2) { onSelect(2) }
        Tab("auxshare", selected == 3) { onSelect(3) }
    }
}

@Composable
private fun Tab(label: String, active: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = 17.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = Type.callout,
            color = if (active) Color.White else glass.ink,
        )
    }
}
