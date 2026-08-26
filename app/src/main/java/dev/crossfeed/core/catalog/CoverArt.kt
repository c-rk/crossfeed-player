package dev.crossfeed.core.catalog

import dev.crossfeed.core.Http
import dev.crossfeed.core.Json
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Musicbrainz for the record and the cover art archive for its sleeve.
 *
 * This is the last thing asked, because it is the slowest and it insists on being asked politely.
 * It is worth having because it is an open catalogue: regional pressings, small labels and older
 * releases live here long after the shops have stopped listing them.
 *
 * It also needs the most care. Musicbrainz keeps every bootleg anyone has ever taped, and ranks a
 * soundboard recording of a festival exactly as highly as the album the song came from, so taking
 * the first answer means calling a song's album "2003 Alpine Valley, East Troy". Releases are
 * therefore sorted before anything is believed.
 */
object CoverArt {

    private const val AGENT = "crossfeed/0.6 (https://github.com/c-rk/crossfeed-player-release)"
    private const val GAP_MS = 1100L
    private const val TRIES = 2

    private var lastCall = 0L

    private class Candidate(
        val recording: JSONObject,
        val release: JSONObject?,
        val rank: Int,
    )

    fun find(title: String, artist: String?, durationMs: Int?): Catalog.Record? {
        val query = buildString {
            append("recording:\"").append(title.replace("\"", "")).append("\"")
            if (!artist.isNullOrBlank()) append(" AND artist:\"").append(artist.replace("\"", "")).append("\"")
        }
        val url = "https://musicbrainz.org/ws/2/recording/?query=" +
            URLEncoder.encode(query, "UTF-8") + "&fmt=json&limit=15"
        val body = polite { Http.get(url, accept = "application/json", userAgent = AGENT)?.body } ?: return null
        val list = Json.parse(body)?.optJSONArray("recordings") ?: return null

        val candidates = mutableListOf<Candidate>()
        for (index in 0 until list.length()) {
            val recording = list.optJSONObject(index) ?: continue
            if (recording.optInt("score") < 80) continue
            if (recording.optString("title").isBlank()) continue
            val length = recording.optLong("length").takeIf { it > 0 }?.toInt()
            if (durationMs != null && length != null && abs(durationMs - length) > 15_000) continue

            val releases = recording.optJSONArray("releases")
            if (releases == null || releases.length() == 0) {
                candidates.add(Candidate(recording, null, 0))
                continue
            }
            for (spot in 0 until releases.length()) {
                val release = releases.optJSONObject(spot) ?: continue
                candidates.add(Candidate(recording, release, rankOf(release)))
            }
        }
        if (candidates.isEmpty()) return null
        candidates.sortByDescending { it.rank }

        val leader = candidates.first()
        val artwork = candidates.asSequence()
            .filter { it.release != null }
            .take(TRIES)
            .mapNotNull { sleeve(it.release!!) }
            .firstOrNull()

        return Catalog.Record(
            title = leader.recording.optString("title"),
            artist = leader.recording.optJSONArray("artist-credit")?.optJSONObject(0)
                ?.optJSONObject("artist")?.optString("name")?.takeIf { it.isNotBlank() },
            // a live bootleg is a real release but a wrong answer to what album this song is on
            album = leader.release?.optString("title")?.takeIf { leader.rank > 0 && it.isNotBlank() },
            durationMs = leader.recording.optLong("length").takeIf { it > 0 }?.toInt(),
            artwork = artwork,
            source = "musicbrainz",
        )
    }

    /** An album beats a single beats anything, and a taped concert loses to all of them. */
    private fun rankOf(release: JSONObject): Int {
        val group = release.optJSONObject("release-group")
        val secondary = group?.optJSONArray("secondary-types")
        if (secondary != null && secondary.length() > 0) return -1
        return when (group?.optString("primary-type")) {
            "Album" -> 3
            "Single" -> 2
            "EP" -> 1
            else -> 0
        }
    }

    /**
     * The archive answers with a redirect for a direct image, and a not found for a release nobody
     * has scanned. Asking for the listing instead gives a settled address and a plain absence, so
     * a dead link is never written down as artwork.
     *
     * The group is asked before the pressing, since art is usually filed once for a record rather
     * than again for each country that sold it.
     */
    private fun sleeve(release: JSONObject): String? {
        val group = release.optJSONObject("release-group")?.optString("id")?.takeIf { it.isNotBlank() }
        val pressing = release.optString("id").takeIf { it.isNotBlank() }
        for (path in listOfNotNull(group?.let { "release-group/$it" }, pressing?.let { "release/$it" })) {
            // the archive is a different house to musicbrainz and does not ask to be slowed down
            val body = Http.get(
                "https://coverartarchive.org/$path",
                accept = "application/json",
                userAgent = AGENT,
            )?.body ?: continue
            pick(Json.parse(body)?.optJSONArray("images"))?.let { return it }
        }
        return null
    }

    private fun pick(images: org.json.JSONArray?): String? {
        if (images == null) return null
        val fronts = (0 until images.length()).mapNotNull { images.optJSONObject(it) }
        val chosen = fronts.firstOrNull { it.optBoolean("front", false) } ?: fronts.firstOrNull() ?: return null
        val thumbnails = chosen.optJSONObject("thumbnails")
        val address = listOf("500", "large", "250", "small")
            .firstNotNullOfOrNull { size -> thumbnails?.optString(size)?.takeIf { it.isNotBlank() } }
            ?: chosen.optString("image").takeIf { it.isNotBlank() }
            ?: return null
        return address.replace("http://", "https://")
    }

    /** Musicbrainz asks for no more than one call a second, and it is their house. */
    private fun <T> polite(call: () -> T): T {
        synchronized(this) {
            val since = System.currentTimeMillis() - lastCall
            if (since in 0 until GAP_MS) Thread.sleep(GAP_MS - since)
            lastCall = System.currentTimeMillis()
        }
        return call()
    }
}
