package dev.crossfeed.core

import android.net.Uri

object LinkParser {

    private val hosts = mapOf(
        "open.spotify.com" to Platform.SPOTIFY,
        "play.spotify.com" to Platform.SPOTIFY,
        "spotify.link" to Platform.SPOTIFY,
        "music.apple.com" to Platform.APPLE_MUSIC,
        "geo.music.apple.com" to Platform.APPLE_MUSIC,
        "itunes.apple.com" to Platform.APPLE_MUSIC,
        "music.youtube.com" to Platform.YOUTUBE_MUSIC,
    )

    private val universalHosts = setOf("song.link", "album.link", "odesli.co", "pods.link", "crossfeed.live")

    private val keptParams = setOf("i", "v", "id")

    private val urlPattern = Regex("""https?://[^\s<>"']+""")

    fun platformOf(url: String): Platform? = hosts[hostOf(url)]

    fun isKnown(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return hosts.containsKey(host) || universalHosts.contains(host)
    }

    fun firstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val candidates = urlPattern.findAll(text).map { it.value.trimEnd('.', ',', ')', ']') }
        return candidates.firstOrNull { isKnown(it) } ?: candidates.firstOrNull()
    }

    fun normalize(url: String): String = runCatching {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase()?.removePrefix("www.") ?: return url
        val builder = Uri.Builder()
            .scheme("https")
            .authority(host)
            .path(uri.path?.trimEnd('/'))
        for (name in uri.queryParameterNames) {
            if (name.lowercase() in keptParams) {
                uri.getQueryParameter(name)?.let { builder.appendQueryParameter(name, it) }
            }
        }
        builder.build().toString()
    }.getOrDefault(url)

    private fun hostOf(url: String): String? =
        runCatching { Uri.parse(url).host?.lowercase() }.getOrNull()
}
