package space.app.understand

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import space.app.data.ItemType

class FilingTest {
    private val now = LocalDateTime.parse("2026-11-25T14:00")
    private fun drafts(text: String) = drafts(understand(text, now).single(), text)

    @Test fun reminderKeepsTheTitleTimeAndRepeat() =
        assertEquals(listOf(Draft(ItemType.REMINDER, "take my pills", LocalDateTime.parse("2026-11-26T08:00"), "FREQ=DAILY")),
            drafts("remind me to take my pills every day at 8"))

    // Events are reminders marked as events, for the calendar.
    @Test fun eventBecomesAReminderMarkedAsEvent() {
        val d = drafts("dentist thursday 3pm").single()
        assertEquals(ItemType.REMINDER, d.type)
        assertEquals(true, d.event)
        assertEquals(false, drafts("remind me to call mom at 6").single().event)
    }

    @Test fun listBecomesShoppingItems() =
        assertEquals(listOf("milk", "eggs", "bread"), drafts("buy milk, eggs and bread").map { it.text })

    @Test fun noteKeepsWhatYouWrote() =
        assertEquals(listOf(Draft(ItemType.NOTE, "wifi password is hunter2")), drafts("wifi password is hunter2"))

    @Test fun taskUsesTheTitle() = assertEquals(listOf(Draft(ItemType.TASK, "call the bank")), drafts("remind me to call the bank"))

    @Test fun questionsAndDiscardsSaveNothing() {
        assertEquals(emptyList<Draft>(), drafts("what's on my grocery list?"))
        assertEquals(emptyList<Draft>(), drafts("um"))
    }

    // #tags written in the capture stay with the item.
    @Test fun tagsSurvive() = assertEquals("call the bank #money", drafts("call the bank #money").single().text)
}
