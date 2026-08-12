package dev.crossfeed.core.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class Account(context: Context) {

    private val store = context.applicationContext
        .getSharedPreferences("crossfeed_account", Context.MODE_PRIVATE)

    val id: String? get() = store.getString(KEY_ID, null)
    val handle: String? get() = store.getString(KEY_HANDLE, null)
    val token: String? get() = store.getString(KEY_TOKEN, null)
    val exists: Boolean get() = token != null

    var claiming: String?
        get() = store.getString(KEY_CLAIM, null)
        set(value) = store.edit().putString(KEY_CLAIM, value).apply()

    fun save(id: String, handle: String, token: String) {
        store.edit()
            .putString(KEY_ID, id)
            .putString(KEY_HANDLE, handle)
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun forget() = store.edit().clear().apply()

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_HANDLE = "handle"
        private const val KEY_TOKEN = "token"
        private const val KEY_CLAIM = "claiming"

        suspend fun register(context: Context, handle: String): String = withContext(Dispatchers.IO) {
            val response = Api.post(
                context,
                "/v1/register",
                JSONObject().put("handle", handle).put("display", handle),
            )
            val account = Account(context)
            val claimed = response.optString("claim") == "pending"
            account.save(
                response.optString("id"),
                response.optString("handle"),
                response.optString("token"),
            )
            account.claiming = if (claimed) response.optString("handle") else null
            response.optString("handle")
        }

        suspend fun settle(context: Context): Boolean = withContext(Dispatchers.IO) {
            val account = Account(context)
            if (account.claiming == null) return@withContext true
            val response = runCatching { Api.get(context, "/v1/me") }.getOrNull() ?: return@withContext false
            if (response.optString("claim").isNotBlank()) return@withContext false
            val handle = response.optString("handle")
            if (handle.isBlank()) return@withContext false
            account.save(response.optString("id"), handle, account.token.orEmpty())
            account.claiming = null
            true
        }
    }
}
