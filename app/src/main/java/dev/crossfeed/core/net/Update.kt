package dev.crossfeed.core.net

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import dev.crossfeed.BuildConfig
import dev.crossfeed.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Notice(
    val text: String,
    val link: String?,
    val at: Long,
)

data class Newer(
    val version: String,
    val apk: String,
    val page: String,
)

/**
 * Whether there is anything to tell the listener, and the means to act on it.
 *
 * Two separate things arrive here. A notice is written by hand on the server and can say anything.
 * A newer version is read from the releases on github, which is where the app is published, so the
 * app can offer to fetch it rather than only mention that it exists.
 */
object Update {

    private const val LATEST =
        "https://api.github.com/repos/c-rk/crossfeed-player-release/releases/latest"
    private const val A_DAY = 24 * 3600_000L

    var notice by mutableStateOf<Notice?>(null)
        private set

    var newer by mutableStateOf<Newer?>(null)
        private set

    var fetching by mutableStateOf(false)
        private set

    var trouble by mutableStateOf<String?>(null)
        private set

    suspend fun check(context: Context) = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)

        val fetched = runCatching {
            val body = Api.get(context, "/v1/banner").optJSONObject("banner") ?: return@runCatching null
            val text = body.optString("text").takeIf { it.isNotBlank() } ?: return@runCatching null
            Notice(text = text, link = body.stringOrNull("link"), at = body.optLong("at"))
        }.getOrNull()
        notice = fetched?.takeIf { it.at > prefs.noticeSeen }

        // github allows sixty of these an hour per address, and a version does not appear more than
        // once a day, so asking once a day is plenty
        if (System.currentTimeMillis() - prefs.updateCheckedAt < A_DAY) return@withContext
        prefs.updateCheckedAt = System.currentTimeMillis()
        newer = runCatching { look() }.getOrNull()
            ?.takeIf { it.version != prefs.versionSeen }
    }

    private fun look(): Newer? {
        val body = read(LATEST) ?: return null
        val release = JSONObject(body)
        val tag = release.optString("tag_name").removePrefix("v").trim()
        if (tag.isBlank() || !isNewer(tag, BuildConfig.VERSION_NAME)) return null
        val assets = release.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk")) {
                return Newer(
                    version = tag,
                    apk = asset.optString("browser_download_url"),
                    page = release.optString("html_url"),
                )
            }
        }
        return null
    }

    /** Compares the parts as numbers, so 0.5.10 comes after 0.5.9 rather than before it. */
    private fun isNewer(candidate: String, running: String): Boolean {
        val left = candidate.split('.').map { it.toIntOrNull() ?: 0 }
        val right = running.split('.').map { it.toIntOrNull() ?: 0 }
        for (index in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(index) { 0 }
            val b = right.getOrElse(index) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /**
     * Fetches the apk and hands it to android's installer. Nothing is installed quietly: the
     * system asks, every time, and it should.
     */
    suspend fun fetch(context: Context, release: Newer) = withContext(Dispatchers.IO) {
        if (fetching) return@withContext
        fetching = true
        trouble = null
        try {
            val folder = File(context.filesDir, "updates").apply { mkdirs() }
            folder.listFiles()?.forEach { it.delete() }
            val file = File(folder, "crossfeed-player-${release.version}.apk")

            val connection = (URL(release.apk).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "crossfeed")
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            if (file.length() < 1_000_000) {
                trouble = "that download did not finish"
                return@withContext
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val install = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(install)
        } catch (error: Exception) {
            Log.w("Update", "could not fetch the update", error)
            trouble = "could not fetch it, the page is still there"
        } finally {
            fetching = false
        }
    }

    fun dismiss(context: Context) {
        notice?.let { Prefs(context).noticeSeen = it.at }
        notice = null
    }

    /** A version put aside stays aside until the next one. */
    fun setAside(context: Context) {
        newer?.let { Prefs(context).versionSeen = it.version }
        newer = null
    }

    fun openPage(context: Context) {
        newer?.let { dev.crossfeed.core.Opener.openWeb(context, it.page) }
    }

    private fun read(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "crossfeed")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()
}
