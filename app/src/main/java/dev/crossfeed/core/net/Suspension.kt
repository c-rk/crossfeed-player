package dev.crossfeed.core.net

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Suspension {

    var active by mutableStateOf(false)
        private set

    var reason by mutableStateOf<String?>(null)
        private set

    private fun store(context: Context) =
        context.applicationContext.getSharedPreferences("crossfeed_account", Context.MODE_PRIVATE)

    fun load(context: Context) {
        active = store(context).getBoolean(KEY_ACTIVE, false)
        reason = store(context).getString(KEY_REASON, null)
    }

    fun mark(context: Context, why: String?) {
        if (active && reason == why) return
        active = true
        reason = why
        store(context).edit().putBoolean(KEY_ACTIVE, true).putString(KEY_REASON, why).apply()
    }

    fun clear(context: Context) {
        if (!active) return
        active = false
        reason = null
        store(context).edit().remove(KEY_ACTIVE).remove(KEY_REASON).apply()
    }

    private const val KEY_ACTIVE = "suspended"
    private const val KEY_REASON = "suspend_reason"
}
