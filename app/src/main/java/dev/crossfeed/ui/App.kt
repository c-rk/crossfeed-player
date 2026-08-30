package dev.crossfeed.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.crossfeed.ui.settings.SettingsScreen
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.launch

/**
 * Four pages, side by side, swiped between.
 *
 * There are no labels under the nav any more. The app is four places and a thumb learns where they
 * are in a day, so the pill only has to say which one you are on, not what each is called.
 */
@Composable
fun CrossfeedApp() {
    val nav = rememberNav()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = Roots.indexOf(nav.root).coerceAtLeast(0)) { Roots.size }
    val glass = LocalGlass.current

    BackHandler(enabled = nav.canPop) { nav.pop() }

    LaunchedEffect(pager.currentPage) { nav.select(Roots[pager.currentPage]) }

    val page = Roots.getOrElse(pager.currentPage) { Dest.HOME }
    val friends = page == Dest.AUX

    Bloom(
        // the aux is the one page about other people, so its light is sage and the accent is the
        // thing kept low and to the side
        top = if (friends) glass.sage else glass.accent,
        topAlpha = if (friends) 0.30f else glass.bloomAlpha,
        bottom = when (page) {
            Dest.AUX -> glass.accent
            Dest.HOME -> glass.sage
            else -> null
        },
        bottomAlpha = if (friends) glass.bloomAlpha else 0.22f,
        bottomLeft = !friends,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            if (nav.canPop) {
                when (nav.current) {
                    Dest.ROUTE -> Sub("route", nav::pop) { RouteScreen() }
                    else -> Unit
                }
            } else {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { index ->
                    when (Roots[index]) {
                        Dest.AUX -> SocialScreen()
                        Dest.PLAYER -> PlayerScreen()
                        Dest.SETTINGS -> SettingsScreen(nav)
                        else -> ListeningScreen()
                    }
                }
            }
        }
        PlayerSheet {
            NavPill(
                selected = page,
                onSelect = { dest -> scope.launch { pager.animateScrollToPage(Roots.indexOf(dest)) } },
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
                Text("back", style = Type.tag, color = glass.t3)
            }
            Spacer(Modifier.padding(horizontal = Space.tight))
            Text(title, style = Type.section, color = glass.t1)
        }
        content()
    }
}

/**
 * Four dots, one of them stretched. The stretched one is where you are, and it wears the colour of
 * whatever service the app is routing through.
 */
@Composable
private fun NavPill(selected: Dest, onSelect: (Dest) -> Unit, modifier: Modifier = Modifier) {
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .background(glass.p2)
            .border(1.dp, glass.bd, shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (dest in Roots) {
            val on = dest == selected
            Box(
                Modifier
                    .size(width = 34.dp, height = 30.dp)
                    .clip(shape)
                    .clickable { onSelect(dest) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .width(if (on) 26.dp else 7.dp)
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(if (on) glass.accent else glass.dot),
                )
            }
        }
    }
}
