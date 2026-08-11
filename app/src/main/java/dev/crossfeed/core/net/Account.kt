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

        suspend fun register(context: Context, handle: String): String = withContext(Dispatchers.IO) {
            val response = Api.post(
                context,
                "/v1/register",
                JSONObject().put("handle", handle).put("display", handle),
            )
            val account = Account(context)
            account.save(
                response.optString("id"),
                response.optString("handle"),
                response.optString("token"),
            )
            response.optString("handle")
        }
    }
}
