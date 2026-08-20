package dev.crossfeed.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.crossfeed.core.Ask
import dev.crossfeed.core.Permissions
import dev.crossfeed.core.Permit
import dev.crossfeed.ui.Dest
import dev.crossfeed.ui.GlassButton
import dev.crossfeed.ui.GlassCard
import dev.crossfeed.ui.Nav
import dev.crossfeed.ui.SectionHeader
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(nav: Nav) {
    val glass = LocalGlass.current
    val owner = LocalLifecycleOwner.current
    var reads by remember { mutableIntStateOf(0) }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reads++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.large),
    ) {
        Spacer(Modifier.height(Space.medium))
        Text("settings", style = Type.wordmark, color = glass.ink)

        Spacer(Modifier.height(Space.medium))
        GlassCard {
            SectionHeader("routing")
            EntryRow(
                title = "route",
                subtitle = "which apps links open in, and who owns them",
                onClick = { nav.push(Dest.ROUTE) },
            )
        }

        Spacer(Modifier.height(Space.medium))
        PermissionsCard(reads)

        Spacer(Modifier.height(Space.medium))
        CaptureCard()

        Spacer(Modifier.height(Space.medium))
        LyricsCard()

        Spacer(Modifier.height(Space.medium))
        LanguagePacksCard()

        Spacer(Modifier.height(Space.medium))
        SharingCard()

        Spacer(Modifier.height(Space.medium))
        DataCard()

        Spacer(Modifier.height(Space.medium))
        AccountCard()

        ServerCard()

        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun EntryRow(title: String, subtitle: String, onClick: () -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.small + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.headline, color = glass.ink)
            Text(
                subtitle,
                style = Type.footnote,
                color = glass.inkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text("›", style = Type.headline, color = glass.inkFaint)
    }
}

@Composable
fun PermissionsCard(resumes: Int) {
    val context = LocalContext.current
    val glass = LocalGlass.current

    var permits by remember { mutableStateOf(emptyList<Permit>()) }
    var reads by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<String?>(null) }

    val request = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val permission = pending
        pending = null
        reads++
        if (!granted && permission != null && !rationale(context, permission)) {
            Permissions.openAppSettings(context)
        }
    }

    LaunchedEffect(reads, resumes) {
        permits = withContext(Dispatchers.IO) { Permissions.all(context) }
    }

    GlassCard {
        SectionHeader("permissions")
        Text(
            "none of these are required. each one turns on a part of the app, and you can take " +
                "it back at any time.",
            style = Type.footnote,
            color = glass.inkMuted,
            modifier = Modifier.padding(bottom = Space.tight),
        )
        for (permit in permits) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.small),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(permit.label, style = Type.headline, color = glass.ink)
                    Text(
                        permit.unlocks,
                        style = Type.footnote,
                        color = glass.inkMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (permit.granted) {
                    Playing()
                } else {
                    GlassButton(
                        label = "turn on",
                        compact = true,
                        onClick = {
                            when (val ask = permit.ask) {
                                is Ask.Runtime -> {
                                    pending = ask.permission
                                    request.launch(ask.permission)
                                }
                                is Ask.Screen -> ask.open(context)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Playing() {
    val glass = LocalGlass.current
    Canvas(Modifier.size(22.dp)) {
        val note = glass.positive
        val stem = size.width * 0.09f
        drawRect(
            note,
            Offset(size.width * 0.42f, size.height * 0.10f),
            Size(stem, size.height * 0.62f),
        )
        drawRect(
            note,
            Offset(size.width * 0.42f, size.height * 0.10f),
            Size(size.width * 0.34f, stem),
        )
        drawRect(
            note,
            Offset(size.width * 0.67f, size.height * 0.10f),
            Size(stem, size.height * 0.30f),
        )
        drawCircle(note, size.width * 0.15f, Offset(size.width * 0.30f, size.height * 0.74f))
        drawCircle(note, size.width * 0.12f, Offset(size.width * 0.58f, size.height * 0.42f))
    }
}

private fun rationale(context: android.content.Context, permission: String): Boolean =
    (context as? Activity)?.shouldShowRequestPermissionRationale(permission) ?: false
