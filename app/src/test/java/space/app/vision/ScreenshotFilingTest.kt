package space.app.vision

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import space.app.data.Item
import space.app.data.ItemType
import space.app.understand.OcrLine

class ScreenshotFilingTest {
    private val now = LocalDateTime.parse("2026-11-25T14:00")
    private val untitled = Item(id = 1, type = ItemType.NOTE, text = SCREENSHOT_TITLE, createdAt = 0)

    @Test
    fun labelsFromTextAndApp() {
        assertEquals(listOf("receipt"), labels("Order #40012 delivered\nTotal ₹1,249", app = null))
        assertEquals(listOf("ticket"), labels("Train 12951 · PNR 4521337890", app = null))
        assertEquals(listOf("recipe"), labels("Ingredients\n2 tbsp ghee", app = null))
        assertEquals(listOf("chat"), labels("see you at 7", app = "com.whatsapp"))
        assertEquals(emptyList<String>(), labels("just a photo of a cat", app = null))
    }

    // EDGE_CASES.md §6: a flyer becomes a reminder titled by its biggest line.
    @Test
    fun flyerBecomesAReminder() {
        val lines = listOf(OcrLine("SUMMER JAZZ NIGHT", 48), OcrLine("Saturday 28 November", 20), OcrLine("7 PM onwards", 18))
        val filed = fileScreenshot(untitled, lines, codes = emptyList(), app = null, now = now)
        assertEquals(ItemType.REMINDER, filed.type)
        assertEquals("SUMMER JAZZ NIGHT", filed.text)
        assertEquals(LocalDateTime.parse("2026-11-28T19:00"), filed.remindAt)
        assertEquals("SUMMER JAZZ NIGHT\nSaturday 28 November\n7 PM onwards", filed.ocrText)
    }

    // Something you wrote yourself is never replaced; the screenshot only adds to it.
    @Test
    fun yourOwnTextStays() {
        val mine = untitled.copy(text = "jazz with Sam?")
        val filed = fileScreenshot(mine, listOf(OcrLine("SUMMER JAZZ NIGHT", 48), OcrLine("Saturday 28 November 7 PM", 20)), emptyList(), null, now)
        assertEquals(ItemType.NOTE, filed.type)
        assertEquals("jazz with Sam?", filed.text)
        assertNull(filed.remindAt)
    }

    @Test
    fun plainScreenshotStaysANoteWithLabelsAndLinks() {
        val lines = listOf(OcrLine("Order #40012 delivered", 20), OcrLine("Total ₹1,249", 18))
        val filed = fileScreenshot(untitled, lines, codes = listOf("https://shop.example/track/40012"), app = null, now = now)
        assertEquals(ItemType.NOTE, filed.type)
        assertEquals("Order #40012 delivered #receipt\nhttps://shop.example/track/40012", filed.text)
    }

    @Test
    fun noTextKeepsTheTitle() = assertEquals(SCREENSHOT_TITLE, fileScreenshot(untitled, emptyList(), emptyList(), null, now).text)
}
