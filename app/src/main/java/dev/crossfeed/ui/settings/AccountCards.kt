package dev.crossfeed.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.crossfeed.BuildConfig
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.net.Account
import dev.crossfeed.core.net.Social
import dev.crossfeed.ui.GlassButton
import dev.crossfeed.ui.GlassCard
import dev.crossfeed.ui.SearchField
import dev.crossfeed.ui.SectionHeader
import dev.crossfeed.ui.ToggleRow
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun SharingCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()
    val registered = remember { Account(context).exists }

    var sharePlays by remember { mutableStateOf(prefs.sharePlays) }
    var pausedUntil by remember { mutableLongStateOf(prefs.pausedUntil) }
    var span by remember { mutableIntStateOf(2) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // the countdown has to move on its own, or a finished pause looks like a stuck one
    LaunchedEffect(pausedUntil) {
        while (pausedUntil > System.currentTimeMillis()) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
        now = System.currentTimeMillis()
    }

    GlassCard {
        SectionHeader("sharing")
        if (!registered) {
            Text(
                "pick a handle in auxshare first. until then nothing is shared with anyone.",
                style = Type.footnote,
                color = glass.inkMuted,
                modifier = Modifier.padding(bottom = Space.tight),
            )
        }
        ToggleRow(
            title = "share what i play",
            subtitle = "posts the track, artist and how long, never your whole history",
            checked = sharePlays && registered,
            onChange = {
                // the switch already reads as off without a handle, so writing the preference
                // anyway left it on underneath, quietly arming everything that reads it
                if (!registered) return@ToggleRow
                sharePlays = it
                prefs.sharePlays = it
            },
        )
        if (sharePlays && registered) {
            val paused = pausedUntil > now
            Spacer(Modifier.height(Space.small))
            Text(
                if (paused) "sharing is paused" else "pause sharing for a while",
                style = Type.headline,
                color = if (paused) glass.accent else glass.ink,
            )
            Text(
                if (paused) {
                    "nothing reaches the feed until " + clockOf(pausedUntil) + " \u00b7 " +
                        leftOf(pausedUntil - now)
                } else {
                    "plays stop reaching auxshare for as long as you choose, then carry on by " +
                        "themselves. your own listening history keeps recording either way."
                },
                style = Type.footnote,
                color = glass.inkMuted,
                modifier = Modifier.padding(top = 2.dp),
            )

            if (paused) {
                Spacer(Modifier.height(Space.small))
                GlassButton(
                    label = "share again now",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        prefs.pausedUntil = 0
                        pausedUntil = 0
                        now = System.currentTimeMillis()
                    },
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("for", style = Type.body, color = glass.inkMuted)
                    Spacer(Modifier.weight(1f))
                    Text(spanLabel(SPANS[span]), style = Type.headline, color = glass.ink)
                }
                Slider(
                    value = span.toFloat(),
                    onValueChange = { span = it.toInt().coerceIn(0, SPANS.lastIndex) },
                    valueRange = 0f..SPANS.lastIndex.toFloat(),
                    steps = SPANS.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = glass.accent,
                        activeTrackColor = glass.accent,
                        inactiveTrackColor = glass.fill,
                    ),
                )
                GlassButton(
                    label = "pause",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val until = System.currentTimeMillis() + SPANS[span] * 60_000L
                        prefs.pausedUntil = until
                        pausedUntil = until
                        now = System.currentTimeMillis()
                    },
                )
            }
            Spacer(Modifier.height(Space.small))
        }

    }
}


/** Fifteen minutes to a full day, in steps worth having rather than every minute in between. */
private val SPANS = listOf(15, 30, 45, 60, 90, 120, 180, 240, 360, 480, 720, 1440)

private fun spanLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes minutes"
    minutes == 60 -> "an hour"
    minutes == 1440 -> "a day"
    minutes % 60 == 0 -> "${minutes / 60} hours"
    else -> "${minutes / 60}h ${minutes % 60}m"
}

private fun leftOf(millis: Long): String {
    val minutes = ((millis + 59_999) / 60_000).toInt().coerceAtLeast(0)
    return when {
        minutes < 60 -> "$minutes min left"
        minutes % 60 == 0 -> "${minutes / 60}h left"
        else -> "${minutes / 60}h ${minutes % 60}m left"
    }
}

private fun clockOf(at: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))

@Composable
fun AccountCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val scope = rememberCoroutineScope()
    val account = remember { Account(context) }

    var registered by remember { mutableStateOf(account.exists) }
    var note by remember { mutableStateOf<String?>(null) }

    if (!registered) return

    GlassCard {
        SectionHeader("account")
        Text(
            "wiping removes your handle, posts, saves, reactions, sessions and every " +
                "connection from the crossfeed server. it cannot be undone.",
            style = Type.footnote,
            color = glass.inkMuted,
            modifier = Modifier.padding(bottom = Space.small),
        )
        GlassButton(
            label = "wipe my account",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    runCatching { Social.forget(context) }
                        .onSuccess {
                            account.forget()
                            registered = false
                            note = "everything on the server is gone"
                        }
                        .onFailure {
                            note = "could not reach the server, so nothing was deleted. " +
                                "your account is untouched, try again when you have signal."
                        }
                }
            },
        )
        note?.let {
            Spacer(Modifier.height(Space.tight))
            Text(it, style = Type.footnote, color = glass.inkMuted)
        }
    }
}

@Composable
fun ServerCard() {
    if (!BuildConfig.DEBUG) return

    val context = LocalContext.current
    val glass = LocalGlass.current
    val prefs = remember { Prefs(context) }

    var baseUrl by remember { mutableStateOf(prefs.baseUrl) }
    var note by remember { mutableStateOf<String?>(null) }

    GlassCard {
        SectionHeader("server")
        Text(
            "debug builds only. release builds talk to the shipped address and never show this box.",
            style = Type.footnote,
            color = glass.inkMuted,
            modifier = Modifier.padding(bottom = Space.small),
        )
        SearchField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            placeholder = "https://…",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.small))
        GlassButton(
            label = "save",
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                prefs.baseUrl = baseUrl
                baseUrl = prefs.baseUrl
                note = "pointing at ${prefs.baseUrl}"
            },
        )
        note?.let {
            Spacer(Modifier.height(Space.tight))
            Text(it, style = Type.footnote, color = glass.inkMuted)
        }
    }
}
