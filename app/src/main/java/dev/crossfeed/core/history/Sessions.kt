package dev.crossfeed.core.history

import android.media.session.MediaController
import androidx.compose.runtime.mutableStateMapOf

enum class Command { PLAY, PAUSE, NEXT, PREVIOUS }

/**
 * The live [MediaController] for every music app on the phone, published by [ListeningService].
 *
 * These controllers were already open for reading what plays. Transport and seek come from the
 * same object, so crossfeed can drive spotify or apple music without either app's cooperation.
 */
object Sessions {

    private val live = mutableStateMapOf<String, MediaController>()

    val available: Boolean get() = live.isNotEmpty()

    fun put(pkg: String, controller: MediaController) {
        live[pkg] = controller
    }

    fun keepOnly(packages: Set<String>) {
        live.keys.toList().filterNot { it in packages }.forEach { live.remove(it) }
    }

    fun clear() = live.clear()

    fun controller(pkg: String?): MediaController? = pkg?.let { live[it] }

    fun transport(command: Command, pkg: String?) {
        val controls = controller(pkg)?.transportControls ?: return
        runCatching {
            when (command) {
                Command.PLAY -> controls.play()
                Command.PAUSE -> controls.pause()
                Command.NEXT -> controls.skipToNext()
                Command.PREVIOUS -> controls.skipToPrevious()
            }
        }
    }

    fun seek(positionMs: Long, pkg: String?) {
        val controls = controller(pkg)?.transportControls ?: return
        runCatching { controls.seekTo(positionMs.coerceAtLeast(0L)) }
    }
}
