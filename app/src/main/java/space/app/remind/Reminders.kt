package space.app.remind

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import space.app.MainActivity
import space.app.R
import space.app.ReminderStyle
import space.app.Settings
import space.app.data.Item

/** Alarms and notifications for items. Talks to Android only; ReminderActions decides what happens. */
@Singleton
class Reminders @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: Settings,
) {
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    init {
        notifications.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                    )
                },
                NotificationChannel(CHANNEL_CREATED, "Saved by Space", NotificationManager.IMPORTANCE_LOW),
            ),
        )
    }

    /** Arms the item's alarm for its next fire time, or cancels it if there is none. */
    fun sync(item: Item) {
        val pending = alarmIntent(item.id)
        alarms.cancel(pending)
        val time = fireTime(item) ?: run { pending.cancel(); return }
        val at = epochMillis(time)
        if (settings.reminderStyle == ReminderStyle.ALARM) {
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openIntent(item.id)), pending)
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    fun isArmed(id: Long): Boolean =
        PendingIntent.getBroadcast(context, id.toInt(), fireIntent(id), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null

    /** For the delete path: no alarm and no notification may outlive the item. */
    fun cancelAll(id: Long) {
        val pending = alarmIntent(id)
        alarms.cancel(pending)
        pending.cancel()
        notifications.cancel(TAG_FIRED, id.toInt())
        notifications.cancel(TAG_CREATED, id.toInt())
    }

    fun dismiss(id: Long) = notifications.cancel(TAG_FIRED, id.toInt())

    fun showFired(item: Item) {
        val alarmStyle = settings.reminderStyle == ReminderStyle.ALARM
        val builder = Notification.Builder(context, if (alarmStyle) CHANNEL_ALARMS else CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_capture)
            .setContentTitle(item.text.lineSequence().first())
            .setContentIntent(openIntent(item.id))
            .setCategory(if (alarmStyle) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
            .addAction(action(item.id, ReminderReceiver.DONE, "Done"))
            .addAction(action(item.id, ReminderReceiver.SNOOZE_30, Snooze.MIN_30.label))
            .addAction(action(item.id, ReminderReceiver.SNOOZE_60, Snooze.HOUR_1.label))
            .addAction(action(item.id, ReminderReceiver.SNOOZE_TOMORROW, Snooze.TOMORROW_9.label))
        if (alarmStyle) {
            val fullScreen = PendingIntent.getActivity(
                context, item.id.toInt(),
                Intent(context, AlarmActivity::class.java).putExtra(EXTRA_ID, item.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setFullScreenIntent(fullScreen, true).setOngoing(true)
        }
        val notification = builder.build()
        if (alarmStyle) notification.flags = notification.flags or Notification.FLAG_INSISTENT // rings until you act
        notifications.notify(TAG_FIRED, item.id.toInt(), notification)
    }

    /** Shown when something was saved from outside the app, so a wrong guess is one tap away. Stays until swiped. */
    fun showCreated(item: Item, summary: String) {
        val notification = Notification.Builder(context, CHANNEL_CREATED)
            .setSmallIcon(R.drawable.ic_capture)
            .setContentTitle(summary)
            .setContentText(item.text.lineSequence().first())
            .setContentIntent(openIntent(item.id))
            .addAction(action(item.id, ReminderReceiver.UNDO, "Undo"))
            .addAction(Notification.Action.Builder(null, "Edit", openIntent(item.id)).build())
            .build()
        notifications.notify(TAG_CREATED, item.id.toInt(), notification)
    }

    fun dismissCreated(id: Long) = notifications.cancel(TAG_CREATED, id.toInt())

    private fun fireIntent(id: Long) =
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.FIRE).putExtra(EXTRA_ID, id)

    private fun alarmIntent(id: Long) =
        PendingIntent.getBroadcast(context, id.toInt(), fireIntent(id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun openIntent(id: Long) = PendingIntent.getActivity(
        context, id.toInt(),
        Intent(context, MainActivity::class.java).putExtra(EXTRA_ID, id).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(id: Long, action: String, label: String): Notification.Action {
        val intent = PendingIntent.getBroadcast(
            context, id.toInt(),
            Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Action.Builder(null, label, intent).build()
    }

    companion object {
        const val EXTRA_ID = "space.app.ITEM_ID"
        const val TAG_FIRED = "fired"
        const val TAG_CREATED = "created"
        private const val CHANNEL_REMINDERS = "reminders"
        private const val CHANNEL_ALARMS = "alarms"
        private const val CHANNEL_CREATED = "created"
    }
}
