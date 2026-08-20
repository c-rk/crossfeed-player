package dev.crossfeed.core.history

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadata
import android.net.Uri
import dev.crossfeed.core.Prefs

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

    /** A source whose word cannot be taken for it: every video there is, music or otherwise. */
    fun needsProof(context: Context, pkg: String): Boolean =
        pkg != context.packageName && pkg !in musicApps &&
            (pkg in videoApps || pkg in browsers(context))

    fun allow(context: Context, pkg: String, metadata: MediaMetadata?): Boolean {
        if (pkg == context.packageName) return true
        if (pkg in musicApps) return true

        if (needsProof(context, pkg)) {
            if (!Prefs(context).countBrowserMusic) return false
            if (metadata == null) return false
            // a song has a length; a stream reports none and a lecture reports far too much
            val length = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
            return length in 1..MAX_TRACK_MS
        }

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
