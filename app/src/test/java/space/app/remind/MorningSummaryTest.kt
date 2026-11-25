package space.app.remind

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import space.app.data.Item
import space.app.data.ItemType

class MorningSummaryTest {
    private fun task(id: Long, text: String, done: Boolean = false) = Item(id = id, type = ItemType.TASK, text = text, createdAt = id, done = done)

    // PLAN.md §3: a morning summary of tasks with no time.
    @Test
    fun listsOpenTasksWithoutATime() {
        val items = listOf(
            task(1, "call the bank"), task(2, "fix the tap"), task(3, "done one", done = true),
            Item(id = 4, type = ItemType.REMINDER, text = "timed", createdAt = 4, remindAt = LocalDateTime.parse("2026-11-26T09:00")),
            Item(id = 5, type = ItemType.NOTE, text = "note", createdAt = 5),
        )
        assertEquals("2 open tasks" to "call the bank · fix the tap", morningSummary(items))
    }

    @Test
    fun oneTaskIsSingular() = assertEquals("1 open task" to "call the bank", morningSummary(listOf(task(1, "call the bank"))))

    @Test
    fun nothingToSay() = assertNull(morningSummary(listOf(task(1, "x", done = true))))

    @Test
    fun nextMorning() {
        assertEquals(LocalDateTime.parse("2026-11-26T08:00"), nextAt(LocalDateTime.parse("2026-11-25T14:00"), 8, 0))
        assertEquals(LocalDateTime.parse("2026-11-25T08:00"), nextAt(LocalDateTime.parse("2026-11-25T07:00"), 8, 0))
    }
}
