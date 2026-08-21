package dev.crossfeed.core.player

import android.content.Intent
import android.os.Process
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaSession.Builder(this, PlayerEngine.player(this))
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult =
                    if (allowed(controller)) {
                        MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
                    } else {
                        MediaSession.ConnectionResult.reject()
                    }
            })
            .build()
    }

    /**
     * Anything that holds the session can read what is playing and drive it. A legacy controller
     * used to be waved through, but that version is reported by any app connecting the old way,
     * not only by the system, so the surfaces we actually need are named instead.
     */
    private fun allowed(controller: MediaSession.ControllerInfo): Boolean =
        controller.packageName == packageName ||
            controller.uid == Process.SYSTEM_UID ||
            controller.packageName in SYSTEM_SURFACES

    private companion object {
        /** The system players that legitimately show and control other apps' playback. */
        val SYSTEM_SURFACES = setOf(
            "com.android.systemui",
            "com.google.android.projection.gearhead",
            "com.google.android.googlequicksearchbox",
            "com.google.android.wearable.app",
            "com.google.android.apps.wearable.companion",
            "com.google.android.carassistant",
            "com.google.android.tv",
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) =
        if (allowed(controllerInfo)) session else null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            release()
            session = null
        }
        super.onDestroy()
    }
}
