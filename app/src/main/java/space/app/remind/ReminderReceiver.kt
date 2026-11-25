package space.app.remind

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarm fired, or a notification button was tapped. Short work only (CLAUDE.md). */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: ReminderActions

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Reminders.EXTRA_ID, -1)
        if (id < 0) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    FIRE -> actions.fire(id)
                    DONE -> actions.done(id)
                    SNOOZE_30 -> actions.snooze(id, Snooze.MIN_30)
                    SNOOZE_60 -> actions.snooze(id, Snooze.HOUR_1)
                    SNOOZE_TOMORROW -> actions.snooze(id, Snooze.TOMORROW_9)
                    UNDO -> actions.undo(id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val FIRE = "space.app.FIRE"
        const val DONE = "space.app.DONE"
        const val SNOOZE_30 = "space.app.SNOOZE_30"
        const val SNOOZE_60 = "space.app.SNOOZE_60"
        const val SNOOZE_TOMORROW = "space.app.SNOOZE_TOMORROW"
        const val UNDO = "space.app.UNDO"
    }
}

/** Re-arms alarms after a reboot, an app update, or a time or zone change (EDGE_CASES.md §8). */
@AndroidEntryPoint
class RearmReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: ReminderActions

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                actions.rearmAll()
                MorningAlarm.schedule(context, space.app.Settings(context).morningSummary)
            } finally { pending.finish() }
        }
    }
}
