package dev.crossfeed.core.net

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.crossfeed.MainActivity
import dev.crossfeed.R

object Notifier {

    private const val CHANNEL = "reactions"
    private const val ID = 4801
    private const val ACCENT = 0xFFCBF56A.toInt()
    private var lastSeen = 0L

    fun channel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "reactions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "when someone reacts to what you played"
            },
        )
    }

    suspend fun poll(context: Context) {
        if (!Account(context).exists) return
        val alerts = runCatching { Social.alerts(context) }.getOrNull() ?: return
        val newest = alerts.items.filter { it.fresh }.maxByOrNull { it.at } ?: return
        if (newest.at <= lastSeen) return
        lastSeen = newest.at
        show(context, newest, alerts.unread)
    }

    private fun show(context: Context, alert: Alert, unread: Int) {
        channel(context)
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val line = "${alert.emoji} @${alert.handle} reacted to ${alert.title}"
        val body = if (unread > 1) "$line, and ${unread - 1} more" else line
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_crossfeed)
            .setColor(ACCENT)
            .setColorized(false)
            .setContentTitle("crossfeed")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID, notification) }
    }
}
