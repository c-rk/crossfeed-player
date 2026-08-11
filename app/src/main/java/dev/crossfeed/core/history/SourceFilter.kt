package dev.crossfeed.core.history

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.net.Uri

object SourceFilter {

    private val videoApps = setOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.creator",
        "com.google.android.videos",
        "com.netflix.mediaclient",
        "com.amazon.avod.thirdpartyclient",
        "in.startv.hotstar",
        "com.jio.media.ondemand",
        "com.disney.disneyplus",
        "com.mxtech.videoplayer.ad",
        "com.mxtech.videoplayer.pro",
        "org.videolan.vlc",
        "org.xbmc.kodi",
        "tv.twitch.android.app",
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.facebook.katana",
        "com.snapchat.android",
        "com.whatsapp",
        "org.telegram.messenger",
        "com.reddit.frontpage",
        "com.linkedin.android",
        "com.plexapp.android",
    )

    private val musicApps = setOf(
        "com.spotify.music",
        "com.apple.android.music",
        "com.google.android.apps.youtube.music",
        "com.maxmpz.audioplayer",
        "com.aspiro.tidal",
        "deezer.android.app",
        "com.soundcloud.android",
        "com.amazon.mp3",
        "com.bandcamp.android",
        "org.moire.ultrasonic",
        "com.simplecity.amp_library",
        "com.doubleTwist.androidPlayer",
        "code.name.monkey.retromusic",
        "com.shazam.android",
    )

    private var browsers: Set<String>? = null

    private fun browsers(context: Context): Set<String> = browsers ?: run {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        val found = context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
        browsers = found
        found
    }

    fun allow(context: Context, pkg: String, metadata: MediaMetadata?): Boolean {
        if (pkg in musicApps) return true
        if (pkg in videoApps) return false
        if (pkg in browsers(context)) return false

        if (metadata == null) return false
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        if (artist.isNullOrBlank()) return false

        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        if (duration > MAX_TRACK_MS) return false

        return true
    }

    private const val MAX_TRACK_MS = 45 * 60_000L
}
