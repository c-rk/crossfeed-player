package dev.crossfeed.core.history

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class Playing(
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val artwork: String?,
    val source: String,
    val playing: Boolean,
    private val positionMs: Long,
    private val positionAt: Long,
) {
    val key: String get() = "$title|${artist.orEmpty()}".lowercase()

    fun positionNow(): Long {
        if (!playing) return positionMs
        val drift = SystemClock.elapsedRealtime() - positionAt
        return (positionMs + drift).coerceAtLeast(0L)
    }
}

object NowPlaying {

    var current by mutableStateOf<Playing?>(null)
        private set

    fun set(
        title: String,
        artist: String?,
        album: String?,
        durationMs: Long,
        artwork: String?,
        source: String,
        playing: Boolean,
        positionMs: Long,
    ) {
        val showing = current
        if (!playing && showing != null && showing.source != source) return
        current = Playing(
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            artwork = artwork,
            source = source,
            playing = playing,
            positionMs = positionMs.coerceAtLeast(0L),
            positionAt = SystemClock.elapsedRealtime(),
        )
    }

    fun clear(source: String) {
        if (current?.source == source) current = null
    }
}
