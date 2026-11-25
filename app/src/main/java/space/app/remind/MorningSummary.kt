package space.app.remind

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import space.app.MainActivity
import space.app.R
import space.app.Settings
import space.app.data.Item
import space.app.data.ItemRepository
import space.app.data.ItemType

/** Title and text of the morning summary: open tasks with no time. Null when there are none. */
fun morningSummary(items: List<Item>): Pair<String, String>? {
    val open = items.filter { it.type == ItemType.TASK && !it.done && !it.archived && it.remindAt == null }.sortedBy { it.createdAt }
    if (open.isEmpty()) return null
    return "${open.size} open task${if (open.size == 1) "" else "s"}" to open.joinToString(" · ") { it.text.lineSequence().first() }
}

fun nextAt(now: LocalDateTime, hour: Int, minute: Int): LocalDateTime =
    now.toLocalDate().atTime(hour, minute).let { if (it > now) it else it.plusDays(1) }

/** The optional morning summary (PLAN.md §3): one inexact daily alarm, nothing else in the background. */
object MorningAlarm {
    private const val HOUR = 8

    fun schedule(context: Context, on: Boolean) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = PendingIntent.getBroadcast(context, 0, Intent(context, MorningSummaryReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarms.cancel(intent)
        if (on) alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, epochMillis(nextAt(LocalDateTime.now(), HOUR, 0)), AlarmManager.INTERVAL_DAY, intent)
    }
}

@AndroidEntryPoint
class MorningSummaryReceiver : BroadcastReceiver() {
    @Inject lateinit var repo: ItemRepository
    @Inject lateinit var settings: Settings

    override fun onReceive(context: Context, intent: Intent) {
        if (!settings.morningSummary) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val (title, text) = morningSummary(repo.all()) ?: return@launch
                val notifications = context.getSystemService(NotificationManager::class.java)
                notifications.createNotificationChannel(android.app.NotificationChannel("summary", "Morning summary", NotificationManager.IMPORTANCE_DEFAULT))
                val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                notifications.notify("summary", 0, Notification.Builder(context, "summary")
                    .setSmallIcon(R.drawable.ic_capture).setContentTitle(title).setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text)).setContentIntent(open).setAutoCancel(true).build())
            } finally {
                pending.finish()
            }
        }
    }
}
