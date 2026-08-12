package dev.crossfeed.core.lyrics

import android.content.Context
import android.os.Build
import dev.crossfeed.core.Http
import dev.crossfeed.core.history.HistoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

object LyricsSource {

    private const val AGENT = "crossfeed/0.1 (https://github.com/c-rk/crossfeed-core)"
    private const val RETRY_MISSING_MS = 7L * 86_400_000

    suspend fun find(
        context: Context,
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long,
    ): Lyrics? = withContext(Dispatchers.IO) {
        val db = HistoryDb.get(context)
        val key = "$title|${artist.orEmpty()}".lowercase()

        db.lyrics(key)?.let { cached ->
            if (cached.missing && System.currentTimeMillis() - cached.fetchedAt < RETRY_MISSING_MS) {
                return@withContext null
            }
            if (!cached.missing) {
                return@withContext Lrc.parse(cached.synced, cached.plain, cached.instrumental)
            }
        }

        val found = fetch(title, artist, album, durationMs)
        db.putLyrics(
            key = key,
            synced = found?.optString("syncedLyrics")?.takeIf { it.isNotBlank() },
            plain = found?.optString("plainLyrics")?.takeIf { it.isNotBlank() },
            instrumental = found?.optBoolean("instrumental") == true,
            missing = found == null,
        )
        found?.let {
            Lrc.parse(
                it.optString("syncedLyrics").takeIf { text -> text.isNotBlank() },
                it.optString("plainLyrics").takeIf { text -> text.isNotBlank() },
                it.optBoolean("instrumental"),
            )
        }
    }

    private fun fetch(title: String, artist: String?, album: String?, durationMs: Long): JSONObject? {
        val exact = buildString {
            append("https://lrclib.net/api/get")
            append("?track_name=").append(enc(title))
            append("&artist_name=").append(enc(artist.orEmpty()))
            if (!album.isNullOrBlank()) append("&album_name=").append(enc(album))
            if (durationMs > 0) append("&duration=").append(durationMs / 1000)
        }
        read(exact)?.let { return it }

        val search = "https://lrclib.net/api/search?track_name=${enc(title)}&artist_name=${enc(artist.orEmpty())}"
        val body = Http.get(search, accept = "application/json", userAgent = AGENT)?.body ?: return null
        val results = runCatching { org.json.JSONArray(body) }.getOrNull() ?: return null
        if (results.length() == 0) return null

        val wanted = durationMs / 1000
        var best: JSONObject? = null
        var bestGap = Long.MAX_VALUE
        for (index in 0 until results.length()) {
            val item = results.optJSONObject(index) ?: continue
            val gap = if (wanted > 0) kotlin.math.abs(item.optLong("duration") - wanted) else 0L
            val synced = item.optString("syncedLyrics").isNotBlank()
            val score = if (synced) gap else gap + 20
            if (score < bestGap) {
                bestGap = score
                best = item
            }
        }
        return best?.takeIf { wanted <= 0 || kotlin.math.abs(it.optLong("duration") - wanted) <= 15 }
    }

    private fun read(url: String): JSONObject? {
        val body = Http.get(url, accept = "application/json", userAgent = AGENT)?.body ?: return null
        return runCatching { JSONObject(body) }.getOrNull()?.takeIf { it.has("id") }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    fun romanisable(text: String): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            text.any { it.code > 0x24F && !it.isWhitespace() }

    fun romanise(text: String): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return text
        return runCatching {
            android.icu.text.Transliterator.getInstance("Any-Latin; Latin-ASCII").transliterate(text)
        }.getOrDefault(text)
    }
}
