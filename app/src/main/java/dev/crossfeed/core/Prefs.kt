package dev.crossfeed.core

import android.content.Context
import java.util.Locale

class Prefs(context: Context) {

    private val store = context.applicationContext.getSharedPreferences("crossfeed", Context.MODE_PRIVATE)

    var targets: List<Platform>
        get() {
            val raw = store.getString(KEY_TARGETS, null) ?: return listOf(Platform.APPLE_MUSIC)
            val parsed = raw.split(',').mapNotNull { Platform.byId(it.trim()) }
            return parsed.ifEmpty { listOf(Platform.APPLE_MUSIC) }
        }
        set(value) {
            val clean = value.distinct().ifEmpty { listOf(Platform.APPLE_MUSIC) }
            store.edit().putString(KEY_TARGETS, clean.joinToString(",") { it.id }).apply()
        }

    val primary: Platform get() = targets.first()

    var preferLocal: Boolean
        get() = store.getBoolean(KEY_PREFER_LOCAL, true)
        set(value) = store.edit().putBoolean(KEY_PREFER_LOCAL, value).apply()

    var autoOpen: Boolean
        get() = store.getBoolean(KEY_AUTO_OPEN, true)
        set(value) = store.edit().putBoolean(KEY_AUTO_OPEN, value).apply()

    var baseUrl: String
        get() = store.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE
        set(value) {
            val clean = value.trim().trimEnd('/').takeIf { it.startsWith("https://") && it.length > 10 } ?: return
            store.edit().putString(KEY_BASE_URL, clean).apply()
        }

    var sharePlays: Boolean
        get() = store.getBoolean(KEY_SHARE_PLAYS, false)
        set(value) = store.edit().putBoolean(KEY_SHARE_PLAYS, value).apply()

    /**
     * Sharing held off until this moment. A pause is a time, not a switch, so forgetting to turn
     * sharing back on is impossible.
     */
    var pausedUntil: Long
        get() = store.getLong(KEY_PAUSED_UNTIL, 0)
        set(value) = store.edit().putLong(KEY_PAUSED_UNTIL, value).apply()

    val sharingPaused: Boolean get() = pausedUntil > System.currentTimeMillis()

    /**
     * Whether a browser or a video app may contribute to the diary. Off by default: those sessions
     * are mostly not music, and a wrong entry is harder to undo than a missing one.
     */
    var countBrowserMusic: Boolean
        get() = store.getBoolean(KEY_BROWSER_MUSIC, false)
        set(value) = store.edit().putBoolean(KEY_BROWSER_MUSIC, value).apply()

    var broadcast: Boolean
        get() = store.getBoolean(KEY_BROADCAST, false)
        set(value) = store.edit().putBoolean(KEY_BROADCAST, value).apply()

    val country: String
        get() = (Locale.getDefault().country.takeIf { it.length == 2 } ?: "US").lowercase()

    companion object {
        const val DEFAULT_BASE = "https://crossfeed-api.tiny-violet-c3ae.workers.dev"

        private const val KEY_TARGETS = "targets"
        private const val KEY_PREFER_LOCAL = "prefer_local"
        private const val KEY_AUTO_OPEN = "auto_open"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SHARE_PLAYS = "share_plays"
        private const val KEY_BROWSER_MUSIC = "count_browser_music"
        private const val KEY_PAUSED_UNTIL = "sharing_paused_until"
        private const val KEY_BROADCAST = "broadcast"
    }
}
