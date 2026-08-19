package dev.crossfeed.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
    const val NEARBY = "nearby"
    const val LINKS = "links"

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
        add(
            Permit(
                id = CONTROL,
                label = "live control",
                unlocks = "the moving progress bar, lyrics that scroll with the song, exact timestamps, " +
                    "and play, pause and seek from inside crossfeed",
                ask = Ask.Screen(::openListenerSettings),
                granted = ListeningService.enabled(context),
            ),
        )
        add(
            Permit(
                id = NEARBY,
                label = "location",
                unlocks = "letting people nearby find you, as a distance band and nothing more",
                ask = Ask.Runtime(Manifest.permission.ACCESS_COARSE_LOCATION),
                granted = held(context, Manifest.permission.ACCESS_COARSE_LOCATION),
            ),
        )
        add(
            Permit(
                id = LINKS,
                label = "music links",
                unlocks = "opening spotify, apple music and youtube links inside crossfeed",
                ask = Ask.Screen(::openLinkSettings),
                granted = LinkOwnership.blockers(context).isEmpty(),
            ),
        )
    }

    fun openListenerSettings(context: Context) {
        start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
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
