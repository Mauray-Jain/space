package space.app.remind

import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import space.app.data.ItemRepository

/** What happens when an alarm fires or a notification button is tapped. */
@Singleton
class ReminderActions @Inject constructor(
    private val repo: ItemRepository,
    private val reminders: Reminders,
) {
    suspend fun fire(id: Long, now: LocalDateTime = LocalDateTime.now()) {
        val item = repo.get(id) ?: return
        if (fireTime(item) == null) return
        reminders.showFired(item)
        val repeat = item.repeat
        val remindAt = item.remindAt
        if (repeat != null && remindAt != null) {
            // Move to the next occurrence after this one (or after now, if alarms were missed).
            val next = nextOccurrence(repeat, remindAt, maxOf(remindAt, now))
            repo.update(item.copy(remindAt = next ?: remindAt, snoozedUntil = null, notified = next == null))
        } else {
            repo.update(item.copy(snoozedUntil = null, notified = true))
        }
    }

    suspend fun done(id: Long) {
        reminders.dismiss(id)
        val item = repo.get(id) ?: return
        // A repeating reminder already moved on when it fired; only a one-off is struck through.
        if (item.repeat == null) repo.update(item.copy(done = true))
    }

    suspend fun snooze(id: Long, snooze: Snooze, now: LocalDateTime = LocalDateTime.now()) {
        reminders.dismiss(id)
        val item = repo.get(id) ?: return
        repo.update(item.copy(snoozedUntil = snooze.from(now), notified = false))
    }

    /** Undo on the "saved" notification: really deletes it. */
    suspend fun undo(id: Long) {
        val item = repo.get(id) ?: return
        repo.delete(item)
    }

    /** After a reboot, a time or zone change, or an app update: alarms are gone, set them again. */
    suspend fun rearmAll() = repo.rearmAll()
}
