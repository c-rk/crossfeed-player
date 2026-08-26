package dev.crossfeed.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

data class Claim(val host: String, val ownerPackage: String, val ownerLabel: String)

object LinkOwnership {

    private val probes = listOf(
        "https://open.spotify.com/track/4PTG3Z6ehGkBFwjybzWkR8",
        "https://music.apple.com/us/album/x/1/",
        "https://music.youtube.com/watch?v=dQw4w9WgXcQ",
        "https://tidal.com/browse/track/1",
    )

    fun blockers(context: Context): List<Claim> {
        val manager = context.packageManager
        val self = context.packageName
        val out = mutableListOf<Claim>()
        for (probe in probes) {
            val host = Uri.parse(probe).host ?: continue
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(probe)).addCategory(Intent.CATEGORY_BROWSABLE)
            val resolved = manager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) ?: continue
            val owner = resolved.activityInfo?.packageName ?: continue
            if (owner == self || owner.contains("android") && owner.contains("resolver")) continue
            if (Platform.entries.none { it.pkg == owner }) continue
            out.add(Claim(host, owner, label(context, owner)))
        }
        return out
    }

    private fun label(context: Context, pkg: String): String = runCatching {
        val manager = context.packageManager
        manager.getApplicationLabel(manager.getApplicationInfo(pkg, 0)).toString().lowercase()
    }.getOrDefault(pkg)

    fun openDefaultsFor(context: Context, pkg: String) {
        val intent = Intent(
            android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
            Uri.parse("package:$pkg"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:$pkg"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.recoverCatching { context.startActivity(fallback) }
    }
}
