package dev.crossfeed.core.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import dev.crossfeed.R
import dev.crossfeed.core.history.HistoryDb
import dev.crossfeed.core.history.Stats
import dev.crossfeed.core.history.Summary
import dev.crossfeed.core.net.Account
import dev.crossfeed.core.net.Live
import dev.crossfeed.core.net.Social
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Pages(applicationContext)
}

/**
 * Two fixed pages. Both are read on the widget's own thread in [onDataSetChanged], which the host
 * calls before it asks for views, so neither the database nor the network is touched on the main
 * thread and neither is asked more often than the widget refreshes.
 */
private class Pages(private val context: Context) : RemoteViewsService.RemoteViewsFactory {

    private var week: Summary? = null
    private var live: List<Live> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        week = runCatching { HistoryDb.get(context).summary(sinceDay()) }.getOrNull()
        live = if (Account(context).exists) {
            runCatching { runBlocking { Social.live(context) } }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    override fun onDestroy() = Unit

    override fun getCount() = 2

    override fun getViewAt(position: Int): RemoteViews =
        if (position == 0) stats() else listening()

    private fun stats(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_stats)
        val summary = week
        views.setTextViewText(R.id.time, Stats.minutes(summary?.listenedMs ?: 0))
        views.setTextViewText(R.id.plays, (summary?.totalPlays ?: 0).toString())
        views.setTextViewText(R.id.artists, (summary?.distinctArtists ?: 0).toString())
        return views
    }

    private fun listening(): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_live)
        val rows = listOf(R.id.row1, R.id.row2, R.id.row3)
        if (live.isEmpty()) {
            views.setTextViewText(R.id.row1, "nobody is playing anything")
            views.setTextViewText(R.id.row2, "")
            views.setTextViewText(R.id.row3, "")
            return views
        }
        for ((index, row) in rows.withIndex()) {
            val item = live.getOrNull(index)
            views.setTextViewText(
                row,
                if (item == null) "" else "@" + item.handle + "  " + item.title,
            )
        }
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount() = 2

    override fun getItemId(position: Int) = position.toLong()

    override fun hasStableIds() = true

    /** The same seven day window the listening page opens on. */
    private fun sinceDay(): String {
        val day = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(day.time)
    }
}
