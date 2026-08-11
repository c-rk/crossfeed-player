package dev.crossfeed.core.history

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import dev.crossfeed.core.net.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ListeningService : NotificationListenerService() {

    private lateinit var capture: PlayCapture
    private var sessionManager: MediaSessionManager? = null
    private val attached = HashMap<String, Pair<MediaController, MediaController.Callback>>()

    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        sync(controllers.orEmpty())
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ticks = 0

    private val ticker = Handler(Looper.getMainLooper())
    private val heartbeat = object : Runnable {
        override fun run() {
            if (this@ListeningService::capture.isInitialized) capture.tick()
            if (++ticks % ALERT_EVERY == 0) {
                scope.launch { Notifier.poll(applicationContext) }
            }
            ticker.postDelayed(this, TICK_MS)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        capture = PlayCapture(applicationContext)
        val manager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        sessionManager = manager
        val component = ComponentName(this, ListeningService::class.java)
        runCatching {
            manager.addOnActiveSessionsChangedListener(listener, component)
            sync(manager.getActiveSessions(component))
        }
        ticker.removeCallbacks(heartbeat)
        ticker.postDelayed(heartbeat, TICK_MS)
    }

    override fun onListenerDisconnected() {
        ticker.removeCallbacks(heartbeat)
        if (this::capture.isInitialized) capture.tick()
        sessionManager?.removeOnActiveSessionsChangedListener(listener)
        detachAll()
        super.onListenerDisconnected()
    }

    private fun sync(controllers: List<MediaController>) {
        val live = controllers.filter { it.packageName != packageName }
        val livePackages = live.map { it.packageName }.toSet()

        attached.keys.toList().filterNot { it in livePackages }.forEach { pkg ->
            attached.remove(pkg)?.let { (controller, callback) ->
                runCatching { controller.unregisterCallback(callback) }
            }
        }
        capture.closeAllExcept(livePackages)

        for (controller in live) {
            if (attached.containsKey(controller.packageName)) continue
            attached[controller.packageName] = controller to capture.attach(controller)
        }
    }

    private fun detachAll() {
        attached.values.forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        attached.clear()
    }

    companion object {
        private const val TICK_MS = 10_000L
        private const val ALERT_EVERY = 30

        fun rebind(context: Context) {
            runCatching {
                requestRebind(ComponentName(context, ListeningService::class.java))
            }
        }

        fun enabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            return flat.split(':').any { it.contains(context.packageName) }
        }
    }
}
