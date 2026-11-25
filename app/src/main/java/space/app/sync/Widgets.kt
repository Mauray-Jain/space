package space.app.sync

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import space.app.MainActivity
import space.app.R
import space.app.capture.QuickEntryActivity
import space.app.data.Item
import space.app.data.ItemRepository
import space.app.remind.Reminders
import space.app.remind.fireTime
import space.app.ui.formatWhen

/** The scrollable home-screen widget. Redrawn when items change; no periodic updates. */
@Singleton
class Widgets @Inject constructor(@ApplicationContext private val context: Context) {
    fun refresh(items: List<Item>) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, UpcomingWidget::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget)
        val list = RemoteViews.RemoteCollectionItems.Builder().setViewTypeCount(1)
        upcoming(items).forEach { item ->
            val row = RemoteViews(context.packageName, R.layout.widget_item)
            row.setTextViewText(R.id.widget_item_text, item.text.lineSequence().first())
            row.setTextViewText(R.id.widget_item_when, fireTime(item)?.let(::formatWhen) ?: "Task")
            row.setOnClickFillInIntent(R.id.widget_item, Intent().putExtra(Reminders.EXTRA_ID, item.id))
            list.addItem(item.id, row)
        }
        views.setRemoteAdapter(R.id.widget_list, list.build())
        views.setEmptyView(R.id.widget_list, R.id.widget_empty)
        views.setPendingIntentTemplate(
            R.id.widget_list,
            PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java), PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        views.setOnClickPendingIntent(
            R.id.widget_add,
            PendingIntent.getActivity(context, 2, Intent(context, QuickEntryActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        views.setOnClickPendingIntent(
            R.id.widget_title,
            PendingIntent.getActivity(context, 3, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        manager.updateAppWidget(ids, views)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDeps {
    fun repo(): ItemRepository
}

class UpcomingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { EntryPointAccessors.fromApplication(context, WidgetDeps::class.java).repo().refreshWidgets() } finally { pending.finish() }
        }
    }
}
