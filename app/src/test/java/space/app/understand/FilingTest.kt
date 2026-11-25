package space.app.understand

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import space.app.data.ItemType

class FilingTest {
    private val now = LocalDateTime.parse("2026-11-25T14:00")
    companion object {
        private val understander = java.io.File(System.getProperty("models.dir")!!).let { dir ->
            Understander(java.io.File(dir, "intent-minilm.onnx").readBytes(), java.io.File(dir, "intent-vocab.txt").readLines())
        }
    }

    private fun drafts(text: String) = drafts(understander.understand(text, now).single(), text)

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

    // A title is one stretch of what you wrote: a word the model skipped inside it ("up", "on the") isn't dropped.
    @Test fun titleIsOneStretch() {
        val r = Reading("task", "pick up dry cleaning on the way home", listOf(0..3, 5..6, 8..10, 12..19, 21..22, 24..26, 28..30, 32..35),
            listOf("B-TITLE", "O", "I-TITLE", "I-TITLE", "O", "O", "I-TITLE", "I-TITLE"))
        assertEquals("pick up dry cleaning on the way home", r.stretch("TITLE"))
        assertEquals("", r.stretch("SEARCH"))
    }

    // #tags written in the capture stay with the item.
    @Test fun tagsSurvive() = assertEquals("call the bank #money", drafts("call the bank #money").single().text)
}
