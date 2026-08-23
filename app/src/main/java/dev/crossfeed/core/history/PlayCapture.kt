package dev.crossfeed.core.history

import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import dev.crossfeed.core.net.Publisher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlayCapture(private val context: Context) {

    /** Whether a source has earned its place in the diary yet. */
    private enum class Proof { NONE, PENDING, ACCEPTED, REJECTED }

    private class Session(
        var title: String,
        var artist: String?,
        var album: String?,
        var durationMs: Long,
        var artwork: String?,
        var startedAt: Long,
        var accumulated: Long = 0,
        var playingSince: Long? = null,
        var rowId: Long? = null,
        var committed: Long = 0,
        var finalized: Boolean = false,
        var artworkRetries: Int = 0,
        var proof: Proof = Proof.NONE,
    ) {
        fun key() = "${title.trim().lowercase()}|${artist.orEmpty().trim().lowercase()}"

        fun listened(now: Long) = accumulated + (playingSince?.let { now - it } ?: 0L)
    }

    private val sessions = HashMap<String, Session>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runStartedAt = 0L
    private var runLastSeen = 0L

    fun onMetadata(pkg: String, metadata: MediaMetadata?, state: PlaybackState?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.trim().orEmpty()
        if (metadata == null || title.isEmpty() || !SourceFilter.allow(context, pkg, metadata)) {
            close(pkg)
            return
        }
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
        val existing = sessions[pkg]
        if (existing != null && existing.key() == "$title|${artist.orEmpty()}") {
            applyState(pkg, state)
            return
        }
        close(pkg)
        val unproven = SourceFilter.needsProof(context, pkg)
        val session = Session(
            title = title,
            artist = artist?.trim()?.takeIf { it.isNotEmpty() },
            album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM),
            durationMs = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0),
            artwork = artworkOf(metadata, "$title|${artist.orEmpty()}"),
            startedAt = System.currentTimeMillis(),
            proof = if (unproven) Proof.PENDING else Proof.NONE,
        )
        sessions[pkg] = session
        // the sleeve is wanted twenty seconds from now, so the asking starts at once
        Publisher.warmArt(context, title, artist)
        if (unproven) verify(pkg, session, title, artist)
        applyState(pkg, state)
    }

    fun applyState(pkg: String, state: PlaybackState?) {
        val session = sessions[pkg] ?: return
        val now = System.currentTimeMillis()
        val playing = state?.state == PlaybackState.STATE_PLAYING
        if (playing && session.playingSince == null) {
            session.playingSince = now
        } else if (!playing && session.playingSince != null) {
            session.accumulated += now - (session.playingSince ?: now)
            session.playingSince = null
        }
        announce(pkg, session, playing, state)
        persist(pkg, session)
    }

    private fun announce(pkg: String, session: Session, playing: Boolean, state: PlaybackState?) {
        // an unproven video is not shown as now playing either, or a lecture leads the page
        if (session.proof == Proof.PENDING || session.proof == Proof.REJECTED) return
        val reported = state?.position ?: 0L
        val since = state?.lastPositionUpdateTime?.takeIf { it > 0 }
            ?.let { android.os.SystemClock.elapsedRealtime() - it } ?: 0L
        NowPlaying.set(
            title = session.title,
            artist = session.artist,
            album = session.album,
            durationMs = session.durationMs,
            artwork = session.artwork,
            source = pkg,
            playing = playing,
            positionMs = reported + if (playing) since else 0L,
        )
    }

    fun close(pkg: String) {
        val session = sessions.remove(pkg) ?: return
        NowPlaying.clear(pkg)
        persist(pkg, session)
        if (!session.finalized && session.rowId != null) {
            session.finalized = true
            HistoryDb.get(context).recordFinish(
                title = session.title,
                source = pkg,
                startedAt = session.startedAt,
                listenedMs = session.listened(System.currentTimeMillis()),
                durationMs = session.durationMs,
            )
        }
    }

    fun tick() {
        sessions.forEach { (pkg, session) -> persist(pkg, session) }
    }

    fun closeAllExcept(packages: Set<String>) {
        sessions.keys.toList().filterNot { it in packages }.forEach(::close)
    }

    fun attach(controller: MediaController): MediaController.Callback {
        val pkg = controller.packageName
        val callback = object : MediaController.Callback() {
            override fun onMetadataChanged(metadata: MediaMetadata?) {
                onMetadata(pkg, metadata, controller.playbackState)
            }

            override fun onPlaybackStateChanged(state: PlaybackState?) {
                applyState(pkg, state)
            }

            override fun onSessionDestroyed() {
                close(pkg)
            }
        }
        controller.registerCallback(callback)
        onMetadata(pkg, controller.metadata, controller.playbackState)
        return callback
    }

    private fun artworkOf(metadata: MediaMetadata, key: String): String? {
        val bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        if (bitmap != null) {
            ArtStore.save(context, key, bitmap)?.let { return it }
        }
        val uri = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI)
        return uri?.takeIf { it.isNotBlank() }
    }


    /**
     * Asks the catalogue whether this video is a record anyone released. Until it answers, the
     * session is held: nothing is written and nothing is shown as playing.
     */
    private fun verify(pkg: String, session: Session, title: String, artist: String?) {
        scope.launch {
            val match = VideoTitles.identify(context, title, artist)
            withContext(Dispatchers.Main) {
                if (sessions[pkg] !== session) return@withContext
                if (match == null) {
                    // a music page names the record it is playing from; a video almost never does,
                    // so an album alongside an artist is worth taking at its word
                    val vouched = !session.artist.isNullOrBlank() && !session.album.isNullOrBlank()
                    session.proof = if (vouched) Proof.ACCEPTED else Proof.REJECTED
                    if (vouched) {
                        announce(pkg, session, session.playingSince != null, null)
                        persist(pkg, session)
                    }
                    return@withContext
                }
                session.title = match.title
                session.artist = match.artist
                session.album = match.album ?: session.album
                if (match.artwork != null) session.artwork = match.artwork
                session.proof = Proof.ACCEPTED
                // the answer arrives after the session went quiet, and a browser may not report
                // anything again for minutes, so what was held back is released here
                announce(pkg, session, session.playingSince != null, null)
                persist(pkg, session)
            }
        }
    }

    private fun persist(pkg: String, session: Session) {
        if (session.proof == Proof.PENDING || session.proof == Proof.REJECTED) return
        val now = System.currentTimeMillis()
        val listened = session.listened(now)
        if (listened < MIN_LISTEN_MS) return
        // a video has to be stayed with longer than a song before it counts as listening
        if (session.proof == Proof.ACCEPTED && listened < PROVEN_MIN_LISTEN_MS) return
        val db = HistoryDb.get(context)

        if (session.rowId == null) {
            val resumed = db.findRecent(session.title, session.artist, pkg, now - RESUME_WINDOW_MS)
            if (resumed != null) {
                val already = db.listenedOf(resumed)
                session.rowId = resumed
                session.accumulated += already
                session.committed = already
            }
        }

        if (session.artwork == null) session.artworkRetries++

        val isNew = session.rowId == null
        if (isNew) {
            session.rowId = db.insert(
                Play(
                    id = 0,
                    title = session.title,
                    artist = session.artist,
                    album = session.album,
                    durationMs = session.durationMs,
                    listenedMs = listened,
                    source = pkg,
                    startedAt = session.startedAt,
                    genre = null,
                    artwork = session.artwork,
                ),
            )
        } else {
            session.rowId?.let { db.updateListened(it, listened) }
        }

        trackRun(now, db)

        val delta = (listened - session.committed).coerceAtLeast(0)
        db.record(
            title = session.title,
            artist = session.artist,
            album = session.album,
            source = pkg,
            startedAt = session.startedAt,
            deltaMs = delta,
            newPlay = isNew,
        )
        session.committed = listened

        Publisher.offer(
            context = context,
            title = session.title,
            artist = session.artist,
            album = session.album,
            source = pkg,
            startedAt = session.startedAt,
            listenedMs = listened,
            durationMs = session.durationMs,
            rowId = session.rowId,
        )
    }

    private fun trackRun(now: Long, db: HistoryDb) {
        if (runStartedAt == 0L || now - runLastSeen > RUN_GAP_MS) {
            runStartedAt = now
        }
        runLastSeen = now
        db.raiseMeta(LONGEST_RUN, runLastSeen - runStartedAt)
    }

    private companion object {
        const val LONGEST_RUN = "longest_run_ms"
        const val RUN_GAP_MS = 5 * 60_000L
        const val MIN_LISTEN_MS = 20_000L
        const val PROVEN_MIN_LISTEN_MS = 60_000L
        const val RESUME_WINDOW_MS = 60 * 60_000L
    }
}
