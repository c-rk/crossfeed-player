package dev.crossfeed.core.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@OptIn(UnstableApi::class)
object PlayerEngine {

    data class State(
        val queue: List<Track> = emptyList(),
        val index: Int = -1,
        val playing: Boolean = false,
        val buffering: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val note: String? = null,
    ) {
        val current: Track? get() = queue.getOrNull(index)
    }

    private const val PREFETCH = 3

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private val queue = mutableListOf<Track>()

    private var exo: ExoPlayer? = null
    private var app: Context? = null
    private var loaded = 0
    private var pumping: Job? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val player = exo ?: return
            val stuck = _state.value.current
            _state.value = _state.value.copy(
                note = stuck?.let { "cannot play ${it.title}, skipping" },
            )
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            } else {
                player.stop()
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            _state.value = _state.value.copy(
                index = player.currentMediaItemIndex.takeIf { player.mediaItemCount > 0 } ?: -1,
                playing = player.isPlaying,
                buffering = player.playbackState == Player.STATE_BUFFERING,
                durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0,
            )
            if (player.isPlaying) startTicker() else stopTicker()
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION)) pump()
        }
    }

    fun player(context: Context): ExoPlayer {
        app = context.applicationContext
        exo?.let { return it }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("crossfeed")
            .setAllowCrossProtocolRedirects(true)
        val created = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context.applicationContext, http)),
            )
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        created.addListener(listener)
        exo = created
        return created
    }

    fun play(context: Context, tracks: List<Track>, startAt: Int = 0) {
        if (tracks.isEmpty()) return
        val player = player(context)
        scope.launch {
            lock.withLock {
                queue.clear()
                queue.addAll(tracks.drop(startAt.coerceIn(0, tracks.lastIndex)))
                loaded = 0
                player.clearMediaItems()
                publish()
            }
            context.startService(Intent(context, PlaybackService::class.java))
            fill(context, until = 1)
            player.prepare()
            player.play()
            pump()
        }
    }

    fun addNext(context: Context, track: Track) = insert(context, track, _state.value.index + 1)

    fun addLast(context: Context, track: Track) = insert(context, track, queue.size)

    private fun insert(context: Context, track: Track, at: Int) {
        val player = player(context)
        scope.launch {
            val position = at.coerceIn(0, queue.size)
            lock.withLock {
                queue.add(position, track)
                if (position < loaded) {
                    val playable = withContext(Dispatchers.IO) { Sources.resolve(context, track) }
                    if (playable == null) {
                        queue.removeAt(position)
                        publish("cannot play ${track.title}")
                        return@withLock
                    }
                    player.addMediaItem(position, item(track, playable))
                    loaded++
                }
                publish()
            }
            if (player.mediaItemCount > 0 && !player.isPlaying && player.playbackState == Player.STATE_IDLE) {
                context.startService(Intent(context, PlaybackService::class.java))
                player.prepare()
            }
            pump()
        }
    }

    fun move(from: Int, to: Int) {
        val player = exo ?: return
        scope.launch {
            lock.withLock {
                if (from !in queue.indices || to !in queue.indices || from == to) return@withLock
                queue.add(to, queue.removeAt(from))
                if (from < loaded && to < loaded) player.moveMediaItem(from, to)
                publish()
            }
        }
    }

    fun remove(at: Int) {
        val player = exo ?: return
        scope.launch {
            lock.withLock {
                if (at !in queue.indices) return@withLock
                queue.removeAt(at)
                if (at < loaded) {
                    player.removeMediaItem(at)
                    loaded--
                }
                publish()
            }
            pump()
        }
    }

    fun toggle() {
        val player = exo ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun skipTo(index: Int) {
        val player = exo ?: return
        if (index in 0 until player.mediaItemCount) {
            player.seekTo(index, 0)
            player.play()
        }
    }

    fun next() = exo?.seekToNextMediaItem()

    fun previous() {
        val player = exo ?: return
        if (player.currentPosition > 4000) player.seekTo(0) else player.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        exo?.seekTo(positionMs)
        _state.value = _state.value.copy(positionMs = positionMs)
    }

    fun clear() {
        scope.launch {
            lock.withLock {
                queue.clear()
                loaded = 0
                exo?.clearMediaItems()
                exo?.stop()
                publish()
            }
        }
    }

    fun release() {
        stopTicker()
        exo?.removeListener(listener)
        exo?.release()
        exo = null
        loaded = 0
        queue.clear()
        _state.value = State()
    }

    private fun pump() {
        val context = app ?: return
        if (pumping?.isActive == true) return
        pumping = scope.launch {
            val target = (_state.value.index.coerceAtLeast(0) + 1 + PREFETCH)
            fill(context, until = target)
        }
    }

    private suspend fun fill(context: Context, until: Int) {
        val player = exo ?: return
        while (true) {
            var track: Track? = null
            lock.withLock {
                if (loaded >= queue.size || loaded >= until) return
                track = queue[loaded]
            }
            val target = track ?: return
            val playable = withContext(Dispatchers.IO) { Sources.resolve(context, target) }
            lock.withLock {
                val position = queue.indexOfFirst { it === target }
                if (position != loaded) return@withLock
                if (playable == null) {
                    queue.removeAt(position)
                    publish("skipped ${target.title}")
                } else {
                    player.addMediaItem(item(target, playable))
                    loaded++
                    publish()
                }
            }
        }
    }

    private fun publish(note: String? = null) {
        val player = exo
        _state.value = _state.value.copy(
            queue = queue.toList(),
            index = player?.currentMediaItemIndex?.takeIf { (player.mediaItemCount) > 0 } ?: -1,
            note = note,
        )
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                exo?.let { player ->
                    _state.value = _state.value.copy(
                        positionMs = player.currentPosition,
                        durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0,
                    )
                }
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun item(track: Track, playable: Playable) = MediaItem.Builder()
        .setUri(playable.uri)
        .setMediaId(track.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.artwork?.let { Uri.parse(it) })
                .build(),
        )
        .build()
}
