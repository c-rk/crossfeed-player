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

    private fun allowed(controller: MediaSession.ControllerInfo): Boolean =
        controller.packageName == packageName ||
            controller.uid == Process.SYSTEM_UID ||
            controller.controllerVersion == MediaSession.ControllerInfo.LEGACY_CONTROLLER_VERSION

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
