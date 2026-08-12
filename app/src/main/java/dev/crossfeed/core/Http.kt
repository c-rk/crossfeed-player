package dev.crossfeed.core

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object Http {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    data class Response(val body: String, val finalUrl: String)

    fun get(
        url: String,
        accept: String = "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
        connectTimeoutMs: Int = 4000,
        readTimeoutMs: Int = 5000,
        maxBytes: Int = 512 * 1024,
        userAgent: String = UA,
    ): Response? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", accept)
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            }
            if (conn.responseCode !in 200..299) return null
            val out = StringBuilder()
            BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                val buffer = CharArray(8192)
                while (out.length < maxBytes) {
                    val read = reader.read(buffer)
                    if (read < 0) break
                    out.appendRange(buffer, 0, read)
                }
            }
            Response(out.toString(), conn.url.toString())
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
