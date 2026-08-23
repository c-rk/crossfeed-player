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

/**
 * Two pages on the home screen: the week's figures, and who is listening right now. A stack rather
 * than a pager because a widget cannot hold a pager, so the pages are swiped through vertically.
 */
class CrossfeedWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_stack)
            val pages = Intent(context, WidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.stack, pages)
            views.setEmptyView(R.id.stack, R.id.empty)

            // the whole widget opens the app; a stack cannot carry a click target of its own
            val open = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            views.setPendingIntentTemplate(R.id.stack, open)
            manager.updateAppWidget(id, views)
        }
    }

    companion object {
        /** Asks every placed widget to read again. Cheap, and the only way in from the app. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, CrossfeedWidget::class.java))
            if (ids.isEmpty()) return
            manager.notifyAppWidgetViewDataChanged(ids, R.id.stack)
        }
    }
}
