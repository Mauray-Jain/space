package space.app.sync

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test
import space.app.data.Item
import space.app.data.ItemType

class MarkdownTest {
    private val created = LocalDateTime.parse("2026-11-25T14:00").toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun fileNameIsReadableAndUnique() {
        assertEquals("call-the-bank-12.md", fileName(Item(id = 12, type = ItemType.TASK, text = "Call the bank!", createdAt = 0)))
        assertEquals("item-7.md", fileName(Item(id = 7, type = ItemType.NOTE, text = "₹₹₹", createdAt = 0)))
        assertEquals(60 + "-1.md".length, fileName(Item(id = 1, type = ItemType.NOTE, text = "a".repeat(200), createdAt = 0)).length)
    }

    @Test
    fun noteWithTagsAndFrontMatter() = assertEquals(
        """
        ---
        type: note
        created: 2026-11-25T14:00Z
        tags: [home]
        ---
        wifi password is hunter2 #home
        """.trimIndent() + "\n",
        markdown(Item(id = 1, type = ItemType.NOTE, text = "wifi password is hunter2 #home", createdAt = created, updatedAt = created), ZoneOffset.UTC),
    )

    @Test
    fun reminderWithRepeatAndScreenshotText() = assertEquals(
        """
        ---
        type: reminder
        created: 2026-11-25T14:00Z
        remind: 2026-11-26T08:00
        repeat: FREQ=DAILY
        done: true
        ---
        take my pills

        > Screenshot text:
        > line one
        > line two
        """.trimIndent() + "\n",
        markdown(
            Item(id = 2, type = ItemType.REMINDER, text = "take my pills", createdAt = created, done = true,
                remindAt = LocalDateTime.parse("2026-11-26T08:00"), repeat = "FREQ=DAILY", ocrText = "line one\nline two"),
            ZoneOffset.UTC,
        ),
    )
}
