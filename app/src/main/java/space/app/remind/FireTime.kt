package space.app.remind

import java.time.LocalDateTime
import java.time.ZoneId
import space.app.data.Item

/** When an item's alarm should go off next, or null if it shouldn't. */
fun fireTime(item: Item): LocalDateTime? =
    if (item.done || item.notified || item.remindAt == null) null else item.snoozedUntil ?: item.remindAt

/** A stored local time in the phone's current zone. A time skipped by DST moves forward. */
fun epochMillis(time: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long = time.atZone(zone).toInstant().toEpochMilli()

enum class Snooze(val label: String) {
    MIN_30("30 min"),
    HOUR_1("1 hour"),
    TOMORROW_9("Tomorrow 9 AM");

    fun from(now: LocalDateTime): LocalDateTime = when (this) {
        MIN_30 -> now.plusMinutes(30)
        HOUR_1 -> now.plusHours(1)
        TOMORROW_9 -> now.toLocalDate().plusDays(1).atTime(9, 0)
    }
}
