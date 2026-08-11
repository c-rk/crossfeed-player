package dev.crossfeed.core.net

import android.content.Context
import dev.crossfeed.core.Prefs
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ApiError(val code: Int, message: String) : Exception(message)

object Api {

    fun get(context: Context, path: String): JSONObject = call(context, "GET", path, null)

    fun post(context: Context, path: String, body: JSONObject = JSONObject()): JSONObject =
        call(context, "POST", path, body)

    fun delete(context: Context, path: String): JSONObject = call(context, "DELETE", path, null)

    fun call(context: Context, method: String, path: String, body: JSONObject?): JSONObject {
        val base = Prefs(context).baseUrl.trimEnd('/')
        val token = Account(context).token
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 6000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() }
            }.orEmpty()
            val parsed = runCatching { JSONObject(text) }.getOrNull() ?: JSONObject()
            if (parsed.optBoolean("suspended")) {
                Suspension.mark(context, parsed.optString("reason").takeIf { it.isNotBlank() })
            } else if (code in 200..299) {
                Suspension.clear(context)
            }
            if (code !in 200..299) {
                throw ApiError(code, parsed.optString("error").ifBlank { "server said $code" })
            }
            return parsed
        } catch (error: ApiError) {
            throw error
        } catch (error: Exception) {
            throw ApiError(0, "cannot reach the server")
        } finally {
            conn?.disconnect()
        }
    }
}
