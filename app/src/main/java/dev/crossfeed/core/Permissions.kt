package dev.crossfeed.core

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dev.crossfeed.core.history.ListeningService

sealed interface Ask {
    data class Runtime(val permission: String) : Ask
    data class Screen(val open: (Context) -> Unit) : Ask
}

data class Permit(
    val id: String,
    val label: String,
    val unlocks: String,
    val ask: Ask,
    val granted: Boolean,
)

object Permissions {

    const val AUDIO = "audio"
    const val NOTIFY = "notify"
    const val CONTROL = "control"
    const val LINKS = "links"
    const val BACKGROUND = "background"

    val audio: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun held(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun all(context: Context): List<Permit> = buildList {
        add(
            Permit(
                id = CONTROL,
                label = "live control",
                unlocks = "this is the one that matters. it is how crossfeed reads the now playing " +
                    "card of every music app, so without it the listening page stays empty. it also " +
                    "gives the moving progress bar, scrolling lyrics, and play, pause, skip and seek " +
                    "for whatever is playing, in any app",
                ask = Ask.Screen(::openListenerSettings),
                granted = ListeningService.enabled(context),
            ),
        )
        add(
            Permit(
                id = BACKGROUND,
                label = "keep listening in the background",
                unlocks = "without it, many phones put crossfeed to sleep after a day and the diary " +
                    "stops noting songs. it costs almost nothing: crossfeed only wakes when music " +
                    "changes. " + batteryStep(context) + brandHint(),
                ask = Ask.Screen(::openBatterySettings),
                granted = unrestricted(context),
            ),
        )
        add(
            Permit(
                id = AUDIO,
                label = "your music files",
                unlocks = "playing the songs stored on this phone",
                ask = Ask.Runtime(audio),
                granted = held(context, audio),
            ),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                Permit(
                    id = NOTIFY,
                    label = "notifications",
                    unlocks = "knowing when a friend reacts or replies",
                    ask = Ask.Runtime(Manifest.permission.POST_NOTIFICATIONS),
                    granted = held(context, Manifest.permission.POST_NOTIFICATIONS),
                ),
            )
        }
        // music links used to be listed here as well as under routing, where the card that can
        // actually fix it lives. one place, and it is the one with the buttons
    }

    fun openListenerSettings(context: Context) {
        start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun unrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * The phone's list of apps and their battery rules. Asking for the exemption directly needs a
     * permission the stores frown on, and the list is one tap further at most.
     */
    fun openBatterySettings(context: Context) {
        // from android 12 the unrestricted choice lives on the app's own page, under battery,
        // and the old list opens filtered to apps that are already exempt
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            openAppSettings(context)
            return
        }
        runCatching { start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            .onFailure { openAppSettings(context) }
    }

    // "allow background usage" being on is the phone's default, optimized; only unrestricted
    // actually leaves crossfeed alone, and it was easy to stop one step short of it
    private fun batteryStep(context: Context): String {
        val held = context.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true
        return if (held) {
            "background use is switched off for crossfeed. turn it on and choose unrestricted."
        } else {
            "background use is allowed but still optimized. choose unrestricted, or don't optimize."
        }
    }

    // the brands that stop listeners hardest each hide a second switch of their own
    private fun brandHint(): String = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> " on this phone, also turn on autostart for crossfeed in its app info."
        "oppo", "realme", "oneplus" -> " on this phone, also allow background activity and auto launch for crossfeed in its app info."
        "vivo", "iqoo" -> " on this phone, also allow background power use and auto start for crossfeed."
        "samsung" -> " on this phone, also make sure crossfeed is not in sleeping or deep sleeping apps."
        "huawei", "honor" -> " on this phone, set crossfeed to manage manually in app launch, with all three switches on."
        else -> ""
    }

    fun openLinkSettings(context: Context) {
        val byDefault = Intent(
            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )
        runCatching { start(context, byDefault) }.onFailure { openAppSettings(context) }
    }

    fun openAppSettings(context: Context) {
        start(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ),
        )
    }

    private fun start(context: Context, intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
