package dev.crossfeed.core

import android.content.Context
import android.content.Intent
import android.net.Uri

object Opener {

    const val POWERAMP = "com.maxmpz.audioplayer"

    fun installed(context: Context, pkg: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun open(context: Context, platform: Platform, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .setPackage(platform.pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (start(context, intent)) return true
        return openWeb(context, url)
    }

    fun openLocal(context: Context, track: LocalTrack): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(track.uri, track.mimeType ?: "audio/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (installed(context, POWERAMP)) {
            val direct = Intent(intent).setPackage(POWERAMP)
            if (start(context, direct)) return true
        }
        return start(context, intent)
    }

    fun openWeb(context: Context, url: String): Boolean =
        start(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )

    fun share(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        start(context, Intent.createChooser(intent, "share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun start(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent); true }.getOrDefault(false)
}
