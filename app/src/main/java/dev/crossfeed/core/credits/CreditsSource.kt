package dev.crossfeed.core.credits

import android.content.Context
import dev.crossfeed.core.Http
import dev.crossfeed.core.Json
import dev.crossfeed.core.history.HistoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs

object CreditsSource {

    private const val AGENT = "crossfeed/0.1 (https://github.com/c-rk/crossfeed-core)"
    private const val PAUSE_MS = 1100L
    private const val RETRY_MISSING_MS = 30L * 86_400_000

    private val wanted = mapOf(
        "producer" to "producer",
        "engineer" to "engineer",
        "recording" to "recording engineer",
        "mix" to "mixing",
        "audio" to "engineer",
        "mastering" to "mastering",
        "vocal" to "vocals",
        "instrument" to "instrument",
        "performer" to "performer",
        "composer" to "composer",
        "lyricist" to "lyricist",
        "writer" to "writer",
        "arranger" to "arranger",
    )

    suspend fun find(
        context: Context,
        title: String,
        artist: String?,
        durationMs: Long,
    ): List<HistoryDb.Credit> = withContext(Dispatchers.IO) {
        val db = HistoryDb.get(context)
        val key = "$title|${artist.orEmpty()}".lowercase()

        db.credits(key)?.let { cached ->
            if (cached.isNotEmpty()) return@withContext cached
        }

        val mbid = recordingId(title, artist, durationMs) ?: run {
            db.putCredits(key, emptyList())
            return@withContext emptyList()
        }
        delay(PAUSE_MS)
        val people = relations(mbid)
        db.putCredits(key, people)
        people
    }

    private fun recordingId(title: String, artist: String?, durationMs: Long): String? {
        val query = buildString {
            append("recording:\"").append(title.replace("\"", "")).append("\"")
            if (!artist.isNullOrBlank()) append(" AND artist:\"").append(artist.replace("\"", "")).append("\"")
        }
        val url = "https://musicbrainz.org/ws/2/recording/?query=" +
            URLEncoder.encode(query, "UTF-8") + "&fmt=json&limit=8"
        val body = Http.get(url, accept = "application/json", userAgent = AGENT)?.body ?: return null
        val list = Json.parse(body)?.optJSONArray("recordings") ?: return null

        var best: String? = null
        var bestGap = Long.MAX_VALUE
        for (index in 0 until list.length()) {
            val item = list.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val length = item.optLong("length")
            val gap = if (durationMs > 0 && length > 0) abs(length - durationMs) else 4000L
            if (gap < bestGap) {
                bestGap = gap
                best = id
            }
        }
        return best?.takeIf { durationMs <= 0 || bestGap <= 12_000 }
    }

    private suspend fun relations(mbid: String): List<HistoryDb.Credit> {
        val url = "https://musicbrainz.org/ws/2/recording/$mbid?inc=artist-rels+work-rels&fmt=json"
        val body = Http.get(url, accept = "application/json", userAgent = AGENT)?.body ?: return emptyList()
        val root = Json.parse(body) ?: return emptyList()
        val out = linkedMapOf<String, HistoryDb.Credit>()

        collect(root.optJSONArray("relations"), out)

        val workId = workOf(root)
        if (workId != null) {
            delay(PAUSE_MS)
            val workUrl = "https://musicbrainz.org/ws/2/work/$workId?inc=artist-rels&fmt=json"
            val workBody = Http.get(workUrl, accept = "application/json", userAgent = AGENT)?.body
            Json.parse(workBody.orEmpty())?.let { collect(it.optJSONArray("relations"), out) }
        }
        return out.values.toList()
    }

    private fun workOf(root: JSONObject): String? {
        val relations = root.optJSONArray("relations") ?: return null
        for (index in 0 until relations.length()) {
            val relation = relations.optJSONObject(index) ?: continue
            if (relation.optString("target-type") != "work") continue
            return relation.optJSONObject("work")?.optString("id")?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun collect(relations: org.json.JSONArray?, out: MutableMap<String, HistoryDb.Credit>) {
        if (relations == null) return
        for (index in 0 until relations.length()) {
            val relation = relations.optJSONObject(index) ?: continue
            val person = relation.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() }
                ?: continue
            val type = relation.optString("type").lowercase()
            val label = wanted.entries.firstOrNull { type.contains(it.key) }?.value ?: continue
            val attributes = relation.optJSONArray("attributes")
            val detail = if (attributes != null && attributes.length() > 0) {
                (0 until attributes.length()).joinToString(", ") { attributes.optString(it) }
            } else {
                label
            }
            val role = if (label == "instrument" || label == "vocals") detail else label
            out["$person|$role"] = HistoryDb.Credit(person, role)
        }
    }
}
