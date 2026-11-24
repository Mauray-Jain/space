package space.app.remind

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import space.app.data.Item
import space.app.data.ItemType

class FireTimeTest {
    private val base = Item(id = 1, type = ItemType.REMINDER, text = "call mom", createdAt = 0, remindAt = LocalDateTime.parse("2026-11-25T18:00"))

    @Test fun firesAtRemindAt() = assertEquals(LocalDateTime.parse("2026-11-25T18:00"), fireTime(base))

    @Test fun snoozeWins() =
        assertEquals(LocalDateTime.parse("2026-11-25T18:30"), fireTime(base.copy(snoozedUntil = LocalDateTime.parse("2026-11-25T18:30"))))

    @Test fun nothingWhenDoneNotifiedOrNoTime() {
        assertNull(fireTime(base.copy(done = true)))
        assertNull(fireTime(base.copy(notified = true)))
        assertNull(fireTime(base.copy(remindAt = null)))
    }

    // EDGE_CASES.md §1: store local time; the alarm follows the phone's current zone, DST included.
    @Test fun localTimeInTheCurrentZone() {
        val ny = ZoneId.of("America/New_York")
        // 2027-03-14 02:30 doesn't exist in New York (clocks jump 02:00 → 03:00): fires at 03:30.
        assertEquals(LocalDateTime.parse("2027-03-14T03:30").atZone(ny).toInstant().toEpochMilli(), epochMillis(LocalDateTime.parse("2027-03-14T02:30"), ny))
    }

    @Test fun snoozeChoices() {
        val now = LocalDateTime.parse("2026-11-25T18:05")
        assertEquals(LocalDateTime.parse("2026-11-25T18:35"), Snooze.MIN_30.from(now))
        assertEquals(LocalDateTime.parse("2026-11-25T19:05"), Snooze.HOUR_1.from(now))
        assertEquals(LocalDateTime.parse("2026-11-26T09:00"), Snooze.TOMORROW_9.from(now))
    }
}
