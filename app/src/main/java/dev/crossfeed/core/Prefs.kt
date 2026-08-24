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

    /**
     * Whether crossfeed may ask the catalogue what genre a track is. Off by default: the question
     * names the track, so it is a disclosure of the diary however small each one is.
     */
    var lookUpGenres: Boolean
        get() = store.getBoolean(KEY_GENRES, false)
        set(value) = store.edit().putBoolean(KEY_GENRES, value).apply()

    /** When the last notice the user waved away was posted. */
    /** When github was last asked whether there is a newer version. */
    var updateCheckedAt: Long
        get() = store.getLong(KEY_CHECKED, 0)
        set(value) = store.edit().putLong(KEY_CHECKED, value).apply()

    /** A version the listener put aside, so it is not offered again until the next one. */
    var versionSeen: String?
        get() = store.getString(KEY_VERSION_SEEN, null)
        set(value) = store.edit().putString(KEY_VERSION_SEEN, value).apply()

    var noticeSeen: Long
        get() = store.getLong(KEY_NOTICE, 0)
        set(value) = store.edit().putLong(KEY_NOTICE, value).apply()

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

    /** Whether sing along may offer what a line means. On, since it costs nothing until used. */
    /**
     * Which catalogue to search. Apple keeps a separate one per country and a release in one is
     * often missing from another, so this follows the phone unless the listener says otherwise,
     * which they will if their subscription is somewhere their phone is not.
     */
    var storeCountry: String?
        get() = store.getString(KEY_STORE, null)
        set(value) = store.edit().putString(KEY_STORE, value?.lowercase()).apply()

    val country: String
        get() = storeCountry
            ?: (Locale.getDefault().country.takeIf { it.length == 2 } ?: "US").lowercase()

    companion object {
        const val DEFAULT_BASE = "https://crossfeed-api.tiny-violet-c3ae.workers.dev"

        private const val KEY_TARGETS = "targets"
        private const val KEY_PREFER_LOCAL = "prefer_local"
        private const val KEY_AUTO_OPEN = "auto_open"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_SHARE_PLAYS = "share_plays"
        private const val KEY_GENRES = "look_up_genres"
        private const val KEY_NOTICE = "notice_seen"
        private const val KEY_CHECKED = "update_checked_at"
        private const val KEY_VERSION_SEEN = "version_seen"
        private const val KEY_STORE = "store_country"
        private const val KEY_BROWSER_MUSIC = "count_browser_music"
        private const val KEY_PAUSED_UNTIL = "sharing_paused_until"
    }
}
