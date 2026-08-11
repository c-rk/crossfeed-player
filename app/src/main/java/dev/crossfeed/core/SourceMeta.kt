package dev.crossfeed.core

import android.net.Uri
import org.json.JSONObject

object SourceMeta {

    private val nextData = Regex(
        """<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    fun read(url: String, platform: Platform?): TrackMeta? = when (platform) {
        Platform.SPOTIFY -> spotify(url)
        Platform.APPLE_MUSIC -> apple(url)
        Platform.YOUTUBE_MUSIC -> generic(url) ?: youtube(url)
        else -> generic(url)
    } ?: generic(url)

    fun kindOf(url: String): EntityKind {
        val path = runCatching { Uri.parse(url).path.orEmpty().lowercase() }.getOrDefault("")
        return when {
            path.contains("/track/") || path.contains("/song/") || path.contains("/watch") -> EntityKind.TRACK
            path.contains("/album/") -> if (url.contains("?i=") || url.contains("&i=")) EntityKind.TRACK else EntityKind.ALBUM
            path.contains("/playlist/") || path.contains("/sets/") -> EntityKind.PLAYLIST
            path.contains("/artist/") -> EntityKind.ARTIST
            else -> EntityKind.UNKNOWN
        }
    }

    private fun spotify(url: String): TrackMeta? {
        val id = Uri.parse(url).pathSegments?.lastOrNull()?.substringBefore('?') ?: return null
        val type = when (kindOf(url)) {
            EntityKind.ALBUM -> "album"
            EntityKind.PLAYLIST -> "playlist"
            EntityKind.ARTIST -> "artist"
            else -> "track"
        }
        val embed = Http.get("https://open.spotify.com/embed/$type/$id") ?: return null
        val blob = nextData.find(embed.body)?.groupValues?.get(1) ?: return null
        val root = Json.parse(blob) ?: return null
        val entity = Json.findObject(root) { obj ->
            obj.has("name") && (obj.has("artists") || obj.has("subtitle") || obj.has("duration"))
        } ?: return null

        val artist = Json.firstName(entity.opt("artists"))
            ?: entity.optString("subtitle").takeIf { it.isNotBlank() }
        val duration = entity.optInt("duration", 0).takeIf { it > 0 }
            ?: entity.optJSONObject("duration")?.optInt("totalMilliseconds")?.takeIf { it > 0 }
        val images = Json.collectStrings(root).filter {
            it.startsWith("https://") && it.contains("/image/") && (it.contains("scdn") || it.contains("spotifycdn"))
        }
        val art = images.firstOrNull { it.contains("ab67616d0000b273") } ?: images.firstOrNull()

        return TrackMeta(
            title = entity.optString("name"),
            artist = artist,
            durationMs = duration,
            artwork = art,
            kind = kindOf(url),
        ).takeIf { it.title.isNotBlank() }
    }

    private fun apple(url: String): TrackMeta? {
        val page = Http.get(url) ?: return null
        val kind = kindOf(url)
        val node = Html.jsonLdObjects(page.body).firstOrNull { obj ->
            obj.optString("@type").startsWith("Music") && obj.optString("name").isNotBlank()
        }
        if (node != null) {
            val audio = node.optJSONObject("audio")
            val source = audio ?: node
            val artist = Json.firstName(source.opt("byArtist")) ?: Json.firstName(node.opt("byArtist"))
            return TrackMeta(
                title = source.optString("name").ifBlank { node.optString("name") },
                artist = artist,
                album = node.optJSONObject("inAlbum")?.optString("name"),
                durationMs = Html.isoDurationMs(source.optString("duration").ifBlank { node.optString("duration") }),
                artwork = Html.meta(page.body, "og:image"),
                kind = kind,
            ).takeIf { it.title.isNotBlank() }
        }
        val ogTitle = Html.meta(page.body, "og:title") ?: return null
        val cleaned = ogTitle.removeSuffix(" on Apple Music").trim()
        val split = cleaned.split(" by ", limit = 2)
        return TrackMeta(
            title = split[0].trim(),
            artist = split.getOrNull(1)?.trim(),
            artwork = Html.meta(page.body, "og:image"),
            kind = kind,
        )
    }

    private fun youtube(url: String): TrackMeta? {
        val id = Uri.parse(url).getQueryParameter("v") ?: return null
        val page = Http.get("https://www.youtube.com/watch?v=$id") ?: return null
        val body = page.body
        val title = Html.meta(body, "og:title") ?: return null
        val artist = Regex(""""author":"(.*?)"""").find(body)?.groupValues?.get(1)
            ?.let { Html.unescape(it) }
            ?.removeSuffix(" - Topic")
        val seconds = Regex(""""lengthSeconds":"(\d+)"""").find(body)?.groupValues?.get(1)?.toIntOrNull()
        return TrackMeta(
            title = title,
            artist = artist,
            durationMs = seconds?.times(1000),
            artwork = Html.meta(body, "og:image"),
            kind = EntityKind.TRACK,
        )
    }

    private fun generic(url: String): TrackMeta? {
        val page = Http.get(url) ?: return null
        val body = page.body
        val node: JSONObject? = Html.jsonLdObjects(body).firstOrNull {
            it.optString("@type").startsWith("Music") || it.has("byArtist")
        }
        val ldTitle = node?.optString("name")?.takeIf { it.isNotBlank() }
        val ldArtist = Json.firstName(node?.opt("byArtist"))

        val ogTitle = Html.meta(body, "og:title") ?: Html.title(body)
        val ogDescription = Html.meta(body, "og:description")
        val rawTitle = ldTitle ?: ogTitle ?: return null

        var title = rawTitle
        var artist = ldArtist

        if (artist == null) {
            when {
                rawTitle.contains(" by ") -> {
                    val parts = rawTitle.split(" by ", limit = 2)
                    title = parts[0].trim()
                    artist = parts[1].substringBefore(" on ").trim()
                }

                rawTitle.contains(" - ") -> {
                    val parts = rawTitle.split(" - ", limit = 2)
                    title = parts[1].trim()
                    artist = parts[0].trim()
                }
            }
        }
        if (artist == null && ogDescription != null) {
            artist = ogDescription.split("·").map { it.trim() }
                .firstOrNull { it.isNotBlank() && !it.equals(title, ignoreCase = true) && it.length < 60 }
        }

        title = title
            .removeSuffix(" on TIDAL")
            .removeSuffix(" | Spotify")
            .removeSuffix(" on Apple Music")
            .trim()

        val durationSeconds = Html.meta(body, "music:duration")?.toIntOrNull()
            ?: Html.meta(body, "og:audio:duration")?.toIntOrNull()

        return TrackMeta(
            title = title,
            artist = artist,
            durationMs = durationSeconds?.times(1000) ?: Html.isoDurationMs(node?.optString("duration")),
            artwork = Html.meta(body, "og:image"),
            kind = kindOf(url),
        ).takeIf { it.title.isNotBlank() }
    }
}
