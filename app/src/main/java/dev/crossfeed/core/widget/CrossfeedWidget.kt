package dev.crossfeed.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.crossfeed.MainActivity
import dev.crossfeed.R
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.Stats
import dev.crossfeed.core.net.Account
import dev.crossfeed.core.net.Social
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Two pages on the home screen: the week in figures, and who is listening right now.
 *
 * A stack view was the obvious choice for swiping between them and turned out to be the wrong one:
 * it lays its children out at a fraction of the widget and offsets them, which left the figures a
 * single character wide. So the pages share one layout, one of them hidden, and the corner flips
 * between them.
 */
class CrossfeedWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) draw(context, manager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != FLIP) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
        val store = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        store.edit().putBoolean(page(id), !store.getBoolean(page(id), false)).apply()
        draw(context, AppWidgetManager.getInstance(context), id)
    }

    /**
     * Reads off the main thread, then hands the finished views to the host. The database is quick
     * but the live row is a request, and a widget that blocks is a widget the system kills.
     */
    private fun draw(context: Context, manager: AppWidgetManager, id: Int) {
        val showLive = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
            .getBoolean(page(id), false)
        val views = RemoteViews(context.packageName, R.layout.widget_root)

        views.setViewVisibility(R.id.page_stats, if (showLive) android.view.View.GONE else android.view.View.VISIBLE)
        views.setViewVisibility(R.id.page_live, if (showLive) android.view.View.VISIBLE else android.view.View.GONE)
        views.setContentDescription(
            R.id.flip,
            if (showLive) "show this week" else "show who is listening",
        )

        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.page_stats, open)
        views.setOnClickPendingIntent(R.id.page_live, open)

        val flip = PendingIntent.getBroadcast(
            context,
            id,
            Intent(context, CrossfeedWidget::class.java).setAction(FLIP)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.flip, flip)

        manager.updateAppWidget(id, views)

        scope.launch {
            val week = runCatching { HistoryDb.get(context).summary(sinceDay()) }.getOrNull()
            views.setTextViewText(R.id.time, Stats.minutes(week?.listenedMs ?: 0))
            views.setTextViewText(R.id.plays, (week?.totalPlays ?: 0).toString())
            views.setTextViewText(R.id.artists, (week?.distinctArtists ?: 0).toString())

            val live = if (Account(context).exists) {
                runCatching { Social.live(context) }.getOrDefault(emptyList())
            } else {
                emptyList()
            }
            val rows = listOf(R.id.row1, R.id.row2, R.id.row3)
            if (live.isEmpty()) {
                views.setTextViewText(R.id.row1, "nobody is playing anything")
                views.setTextViewText(R.id.row2, "")
                views.setTextViewText(R.id.row3, "")
            } else {
                for ((index, row) in rows.withIndex()) {
                    val item = live.getOrNull(index)
                    views.setTextViewText(row, if (item == null) "" else "@" + item.handle + "  " + item.title)
                }
            }
            manager.updateAppWidget(id, views)
        }
    }

    /** The same seven day window the listening page opens on. */
    private fun sinceDay(): String {
        val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day.time)
    }

    private fun page(id: Int) = "page_" + id

    companion object {
        private const val FLIP = "dev.crossfeed.widget.FLIP"
        private const val STORE = "widget"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Asks every placed widget to read again. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CrossfeedWidget::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, CrossfeedWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
            )
        }
    }
}
