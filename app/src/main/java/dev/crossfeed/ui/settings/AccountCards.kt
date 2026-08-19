package dev.crossfeed.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.crossfeed.BuildConfig
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.net.Account
import dev.crossfeed.core.net.Presence
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

@Composable
fun SharingCard() {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val prefs = remember { Prefs(context) }
    val scope = rememberCoroutineScope()
    val registered = remember { Account(context).exists }

    var sharePlays by remember { mutableStateOf(prefs.sharePlays) }
    var broadcast by remember { mutableStateOf(prefs.broadcast) }

    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        broadcast = granted
        prefs.broadcast = granted
        if (granted) scope.launch { Presence.push(context) }
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
                sharePlays = it
                prefs.sharePlays = it
            },
        )
        ToggleRow(
            title = "let people find me nearby",
            subtitle = "rounds your position to ~110 m; others only ever see a distance band",
            checked = broadcast && registered,
            onChange = { value ->
                if (value && !Presence.allowed(context)) {
                    askLocation.launch(Presence.PERMISSION)
                } else {
                    broadcast = value
                    prefs.broadcast = value
                    scope.launch { Presence.push(context) }
                }
            },
        )
    }
}

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
