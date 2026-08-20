package dev.crossfeed.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.crossfeed.core.history.Command
import dev.crossfeed.core.history.NowPlaying
import dev.crossfeed.core.history.Sessions
import dev.crossfeed.core.player.PlayerEngine
import kotlinx.coroutines.delay

/**
 * One song playing, whoever is playing it.
 *
 * Crossfeed's own engine and another app's media session describe the same thing in different
 * shapes. Flattening them here means the mini bar, the full sheet and the listening page each
 * hold a player rather than a local player with an exception bolted on.
 */
@Immutable
data class Deck(
    val id: String,
    val title: String,
    val artist: String?,
    val artwork: String?,
    val positionMs: Long,
    val durationMs: Long,
    val playing: Boolean,
    val local: Boolean,
    val source: String?,
    val controllable: Boolean,
) {
    fun toggle() {
        if (local) {
            PlayerEngine.toggle()
        } else {
            Sessions.transport(if (playing) Command.PAUSE else Command.PLAY, source)
        }
    }

    fun next() {
        if (local) PlayerEngine.next() else Sessions.transport(Command.NEXT, source)
    }

    fun previous() {
        if (local) PlayerEngine.previous() else Sessions.transport(Command.PREVIOUS, source)
    }

    fun seekTo(positionMs: Long) {
        if (local) PlayerEngine.seekTo(positionMs) else Sessions.seek(positionMs, source)
    }
}

/**
 * Crossfeed's own playback wins when it has something, because that is the one the user started
 * here. Anything else on the phone fills the gap.
 */
@Composable
fun rememberDeck(): Deck? {
    val state by PlayerEngine.state.collectAsStateWithLifecycle()
    val now = NowPlaying.current
    var position by remember { mutableLongStateOf(0L) }

    // a session reports a moment and a rate, not a running clock, so it has to be re-read
    LaunchedEffect(now?.key, now?.playing) {
        while (now != null) {
            position = now.positionNow()
            delay(500)
        }
    }

    val track = state.current

    // whatever is actually playing wins. our own engine holds on to its last track after it
    // stops, so preferring it outright would freeze the card on a song that ended hours ago.
    val ours = track != null && (state.playing || now == null || !now.playing)

    return when {
        track != null && ours -> Deck(
            id = track.id,
            title = track.title,
            artist = track.artist,
            artwork = track.artwork,
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            playing = state.playing,
            local = true,
            source = null,
            controllable = true,
        )

        now != null -> Deck(
            id = now.key,
            title = now.title,
            artist = now.artist,
            artwork = now.artwork,
            positionMs = position,
            durationMs = now.durationMs,
            playing = now.playing,
            local = false,
            source = now.source,
            controllable = Sessions.controller(now.source) != null,
        )

        else -> null
    }
}
