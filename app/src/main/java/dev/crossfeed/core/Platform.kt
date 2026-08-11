package dev.crossfeed.core

import java.net.URLEncoder

enum class Platform(
    val id: String,
    val label: String,
    val pkg: String,
    val odesliKey: String,
) {
    APPLE_MUSIC("apple_music", "apple music", "com.apple.android.music", "appleMusic"),
    SPOTIFY("spotify", "spotify", "com.spotify.music", "spotify"),
    YOUTUBE_MUSIC("youtube_music", "youtube music", "com.google.android.apps.youtube.music", "youtubeMusic"),
    TIDAL("tidal", "tidal", "com.aspiro.tidal", "tidal"),
    DEEZER("deezer", "deezer", "deezer.android.app", "deezer");

    fun searchUrl(query: String): String {
        val q = URLEncoder.encode(query, "UTF-8")
        return when (this) {
            APPLE_MUSIC -> "https://music.apple.com/us/search?term=$q"
            SPOTIFY -> "https://open.spotify.com/search/$q"
            YOUTUBE_MUSIC -> "https://music.youtube.com/search?q=$q"
            TIDAL -> "https://tidal.com/search?q=$q"
            DEEZER -> "https://www.deezer.com/search/$q"
        }
    }

    companion object {
        fun byId(id: String?): Platform? = entries.firstOrNull { it.id == id }

        fun byOdesliKey(key: String): Platform? = entries.firstOrNull { it.odesliKey == key }
    }
}
