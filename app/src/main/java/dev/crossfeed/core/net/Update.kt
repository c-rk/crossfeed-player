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
    private const val HALF_A_DAY = 12 * 3600_000L

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

        // the notice is written by hand and changes about never, so asking twice a day is plenty.
        // what it last said is remembered, so the banner still appears without asking again
        val asked = System.currentTimeMillis() - prefs.bannerAskedAt < HALF_A_DAY
        val fetched = if (asked) null else runCatching {
            val body = Api.get(context, "/v1/banner").optJSONObject("banner") ?: return@runCatching null
            val text = body.optString("text").takeIf { it.isNotBlank() } ?: return@runCatching null
            // the banner is one of the few things the server gets to put in front of you, and a
            // tap on it opens whatever it says. anything but a web address is refused, because a
            // scheme like intent: is a way of reaching into other apps rather than a link
            val link = body.stringOrNull("link")?.takeIf { it.startsWith("https://") }
            Notice(text = text, link = link, at = body.optLong("at"))
        }.getOrNull()
        if (!asked) prefs.bannerAskedAt = System.currentTimeMillis()
        fetched?.let { prefs.bannerHeld = it.text + "\u0000" + it.link.orEmpty() + "\u0000" + it.at }
        val held = fetched ?: prefs.bannerHeld?.split("\u0000")?.takeIf { it.size == 3 }?.let {
            Notice(
                text = it[0],
                link = it[1].takeIf { link -> link.startsWith("https://") },
                at = it[2].toLongOrNull() ?: 0,
            )
        }
        notice = held?.takeIf { it.text.isNotBlank() && it.at > prefs.noticeSeen }

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
                // the address of the download comes out of the reply, and what it points at gets
                // handed to the installer, so it has to be github and nowhere else
                val apk = asset.optString("browser_download_url")
                if (!fromGithub(apk)) return null
                return Newer(version = tag, apk = apk, page = release.optString("html_url"))
            }
        }
        return null
    }

    /** Compares the parts as numbers, so 0.5.10 comes after 0.5.9 rather than before it. */
    private fun isNewer(candidate: String, running: String): Boolean {
        // a build can carry a suffix, like the redesign's 0.5.9-glass, and only the numbers count
        fun parts(value: String) = value.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
        val left = parts(candidate)
        val right = parts(running)
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

            if (!fromGithub(release.apk)) {
                trouble = "that download did not come from the right place"
                return@withContext
            }

            val connection = (URL(release.apk).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "crossfeed")
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
            }
            var written = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        written += read
                        // the app is forty odd megabytes. a reply that never ends would otherwise
                        // fill the phone, so it is given a ceiling and no more
                        if (written > MOST_AN_APK_CAN_BE) {
                            file.delete()
                            trouble = "that download was far too big"
                            return@withContext
                        }
                        output.write(buffer, 0, read)
                    }
                }
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

    /** Where a release can legitimately be downloaded from, and nowhere else. */
    private fun fromGithub(url: String): Boolean = runCatching {
        val host = URL(url).host.lowercase()
        url.startsWith("https://") &&
            (host == "github.com" || host == "objects.githubusercontent.com")
    }.getOrDefault(false)

    private fun read(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "crossfeed")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        // a release listing is a few kilobytes of json. reading without a ceiling means a reply
        // that never ends takes the app down with it
        connection.inputStream.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                out.write(buffer, 0, count)
                if (out.size() > MOST_A_REPLY_CAN_BE) return@runCatching null
            }
            out.toString(Charsets.UTF_8.name())
        }
    }.getOrNull()

    private const val MOST_AN_APK_CAN_BE = 200L * 1024 * 1024
    private const val MOST_A_REPLY_CAN_BE = 512 * 1024
}
