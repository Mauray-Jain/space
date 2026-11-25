package space.app.sync

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import space.app.data.Item
import space.app.data.ItemType

class UpcomingTest {
    private fun t(s: String) = LocalDateTime.parse(s)

    // The widget: next reminders first (soonest first, snoozes count), then open tasks; nothing done or archived.
    @Test
    fun soonestRemindersThenOpenTasks() {
        val items = listOf(
            Item(id = 1, type = ItemType.REMINDER, text = "late", createdAt = 1, remindAt = t("2026-11-27T09:00")),
            Item(id = 2, type = ItemType.REMINDER, text = "soon", createdAt = 2, remindAt = t("2026-11-25T18:00")),
            Item(id = 3, type = ItemType.REMINDER, text = "snoozed", createdAt = 3, remindAt = t("2026-11-25T14:00"), snoozedUntil = t("2026-11-26T09:00")),
            Item(id = 4, type = ItemType.TASK, text = "task", createdAt = 4),
            Item(id = 5, type = ItemType.TASK, text = "done task", createdAt = 5, done = true),
            Item(id = 6, type = ItemType.NOTE, text = "note", createdAt = 6),
            Item(id = 7, type = ItemType.TASK, text = "archived", createdAt = 7, archived = true),
        )
        assertEquals(listOf(2L, 3L, 1L, 4L), upcoming(items).map { it.id })
    }
}
