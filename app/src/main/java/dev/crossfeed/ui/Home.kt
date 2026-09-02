package dev.crossfeed.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.crossfeed.core.Cache
import dev.crossfeed.core.Claim
import dev.crossfeed.core.LinkOwnership
import dev.crossfeed.core.LinkParser
import dev.crossfeed.core.LocalLibrary
import dev.crossfeed.core.Opener
import dev.crossfeed.core.Platform
import dev.crossfeed.core.Prefs
import dev.crossfeed.core.Resolved
import dev.crossfeed.core.Resolver
import dev.crossfeed.ui.theme.LocalGlass
import dev.crossfeed.ui.theme.ThemeSeed
import dev.crossfeed.ui.theme.Space
import dev.crossfeed.ui.theme.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RouteScreen(embedded: Boolean = false) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val prefs = remember { Prefs(context) }
    val cache = remember { Cache(context) }
    val scope = rememberCoroutineScope()

    var targets by remember { mutableStateOf(prefs.targets) }
    var preferLocal by remember { mutableStateOf(prefs.preferLocal) }
    var autoOpen by remember { mutableStateOf(prefs.autoOpen) }
    var granted by remember { mutableStateOf(LocalLibrary.hasPermission(context)) }
    var localCount by remember { mutableIntStateOf(0) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Resolved?>(null) }
    var recents by remember { mutableStateOf(cache.recents()) }
    var blockers by remember { mutableStateOf(emptyList<Claim>()) }

    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }

    LaunchedEffect(granted) {
        localCount = withContext(Dispatchers.IO) { LocalLibrary.count(context) }
    }

    LaunchedEffect(Unit) {
        blockers = withContext(Dispatchers.IO) { LinkOwnership.blockers(context) }
    }

    Column(
        if (embedded) {
            Modifier
        } else {
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.large)
        },
    ) {
        if (!embedded) {
            Spacer(Modifier.height(Space.medium))
            Text("crossfeed", style = Type.wordmark, color = glass.ink)
            Text("music anywhere", style = Type.body, color = glass.inkMuted)
            Spacer(Modifier.height(Space.medium))
        }

        // the wheel and the two switches it governs, side by side. stacked, the switches were a
        // scroll away from the thing they qualify
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconWheel(
                platforms = Platform.entries,
                selected = targets,
                installed = { Opener.installed(context, it.pkg) },
                diameter = 212.dp,
                onToggle = { platform ->
                    targets = if (platform in targets) targets - platform else targets + platform
                    if (targets.isEmpty()) targets = listOf(platform)
                    prefs.targets = targets
                    ThemeSeed.pkg = targets.first().pkg
                    // the first route is the colour of the whole app, so changing it here
                    // retints everything rather than waiting for a restart
                    dev.crossfeed.ui.theme.Look.routed(context)
                    result = null
                },
            )
            Spacer(Modifier.width(Space.small))
            Column(Modifier.weight(1f)) {
                WordToggle(
                    title = "my own files first",
                    subtitle = if (granted) "$localCount tracks" else "needs audio access",
                    on = preferLocal && granted,
                    onChange = { value ->
                        if (value && !granted) ask.launch(LocalLibrary.permission())
                        preferLocal = value
                        prefs.preferLocal = value
                    },
                )
                Spacer(Modifier.height(Space.medium))
                WordToggle(
                    title = "open straight away",
                    subtitle = "skip the sheet on an exact match",
                    on = autoOpen,
                    onChange = {
                        autoOpen = it
                        prefs.autoOpen = it
                    },
                )
            }
        }

        Text(
            // the wheel has no middle now, so the name it would have held goes here
            text = if (targets.size <= 1) {
                targets.firstOrNull()?.label.orEmpty() + " · links route straight through"
            } else {
                targets.joinToString(", ") { it.label } + " · crossfeed will ask which"
            },
            style = Type.footnote,
            color = glass.inkMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.small),
        )

        Spacer(Modifier.height(Space.medium))

        GlassCard {
            SectionHeader("try a link")
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "paste a music link",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                GlassButton(label = "paste", onClick = { input = clipboardText(context) ?: input })
            }
            Spacer(Modifier.height(Space.small))
            GlassButton(
                label = if (busy) "resolving…" else "resolve",
                filled = true,
                enabled = !busy && input.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val url = LinkParser.firstUrl(input) ?: return@GlassButton
                    busy = true
                    result = null
                    scope.launch {
                        result = Resolver.resolve(context, url)
                        recents = cache.recents()
                        busy = false
                    }
                },
            )
            result?.let {
                Spacer(Modifier.height(Space.medium))
                RoutePreview(it)
            }
        }


        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(Space.medium))
            GlassCard {
                SectionHeader("recent") {
                    GlassChip(label = "clear", onClick = {
                        cache.clear()
                        recents = emptyList()
                    })
                }
                for (entry in recents.take(6)) {
                    Column(Modifier.padding(vertical = Space.tight)) {
                        Text(entry.title, style = Type.headline, color = glass.ink, maxLines = 1)
                        Text(
                            listOfNotNull(
                                entry.artist,
                                "${entry.sourceLabel} → ${entry.targetLabel}",
                            ).joinToString(" · "),
                            style = Type.footnote,
                            color = glass.inkFaint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.medium))

        GlassCard(shape = dev.crossfeed.ui.theme.Shapes.cardMirror) {
            SectionHeader("link handling")
            if (blockers.isEmpty()) {
                Text(
                    "no other app is claiming music links. turn crossfeed's supported links on " +
                        "and every link lands here.",
                    style = Type.body,
                    color = glass.inkMuted,
                    modifier = Modifier.padding(bottom = Space.medium),
                )
            } else {
                Text(
                    "android gives a verified owner first refusal, so these apps take their own " +
                        "links before crossfeed is ever offered. turn each one's " +
                        "“open supported links” off, then switch crossfeed's on.",
                    style = Type.body,
                    color = glass.inkMuted,
                    modifier = Modifier.padding(bottom = Space.small),
                )
                for (claim in blockers) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = Space.tight),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(claim.ownerLabel, style = Type.headline, color = glass.ink)
                            Text(claim.host, style = Type.footnote, color = glass.inkFaint)
                        }
                        GlassButton(
                            label = "fix",
                            compact = true,
                            onClick = { LinkOwnership.openDefaultsFor(context, claim.ownerPackage) },
                        )
                    }
                }
                Spacer(Modifier.height(Space.small))
            }
            GlassButton(
                label = "crossfeed's link settings",
                filled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = { openLinkSettings(context) },
            )
        }

        if (!embedded) Spacer(Modifier.height(110.dp))
    }
}

@Composable
fun RoutePreview(resolved: Resolved) {
    val context = LocalContext.current
    val glass = LocalGlass.current
    val meta = resolved.meta

    if (meta == null) {
        Text(resolved.error ?: "nothing found", style = Type.headline, color = glass.warning)
        return
    }

    Text(meta.title, style = Type.title, color = glass.ink, maxLines = 2)
    meta.artist?.let { Text(it, style = Type.body, color = glass.inkMuted, maxLines = 1) }
    Spacer(Modifier.height(Space.small))

    resolved.local?.let { local ->
        GlassButton(
            label = "play local · ${local.format}",
            filled = true,
            modifier = Modifier.fillMaxWidth(),
            onClick = { Opener.openLocal(context, local) },
        )
        Spacer(Modifier.height(Space.tight))
    }

    for (route in resolved.routes) {
        GlassButton(
            label = if (route.exact) "open in ${route.platform.label}" else "search ${route.platform.label}",
            filled = resolved.local == null && route == resolved.primary,
            modifier = Modifier.fillMaxWidth(),
            onClick = { Opener.open(context, route.platform, route.url) },
        )
        Spacer(Modifier.height(Space.tight))
    }
}

fun openResolved(context: Context, resolved: Resolved) {
    val local = resolved.local
    if (local != null) {
        Opener.openLocal(context, local)
        return
    }
    val route = resolved.primary ?: return
    Opener.open(context, route.platform, route.url)
}

private fun clipboardText(context: Context): String? {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotBlank() }
}

private fun openLinkSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${context.packageName}"))
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
    runCatching { context.startActivity(intent) }
}


/**
 * A setting with no switch beside it.
 *
 * A switch is a second thing to look at saying what the words already could, and it costs the
 * width the words needed. On, the line is in the accent; off, it is the quiet ink. The whole
 * thing is the target.
 */
@Composable
private fun WordToggle(
    title: String,
    subtitle: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val glass = LocalGlass.current
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!on) }
            .padding(vertical = 2.dp),
    ) {
        Text(
            title,
            style = Type.headline,
            color = if (on) glass.accent else glass.t3,
        )
        Text(
            subtitle,
            style = Type.meta,
            color = glass.t3,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
