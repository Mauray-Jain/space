package space.app.remind

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// EDGE_CASES.md §2. 2026-11-25 is a Wednesday.
class RepeatTest {
    private fun t(s: String) = LocalDateTime.parse(s)
    private fun next(rule: String, start: String, after: String) = nextOccurrence(rule, t(start), t(after))

    @Test fun daily() = assertEquals(t("2026-11-26T08:00"), next("FREQ=DAILY", "2026-11-25T08:00", "2026-11-25T08:00"))

    @Test fun dailyTwiceADay() {
        assertEquals(t("2026-11-25T21:00"), next("FREQ=DAILY;BYHOUR=9,21", "2026-11-25T09:00", "2026-11-25T09:00"))
        assertEquals(t("2026-11-26T09:00"), next("FREQ=DAILY;BYHOUR=9,21", "2026-11-25T09:00", "2026-11-25T21:00"))
    }

    @Test fun weekdays() =
        assertEquals(t("2026-11-30T07:00"), next("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", "2026-11-26T07:00", "2026-11-27T07:00"))

    @Test fun mondayAndThursday() {
        assertEquals(t("2026-11-26T09:00"), next("FREQ=WEEKLY;BYDAY=MO,TH", "2026-11-26T09:00", "2026-11-25T14:00"))
        assertEquals(t("2026-11-30T09:00"), next("FREQ=WEEKLY;BYDAY=MO,TH", "2026-11-26T09:00", "2026-11-26T09:00"))
    }

    @Test fun everyOtherSunday() =
        assertEquals(t("2026-12-13T10:00"), next("FREQ=WEEKLY;INTERVAL=2;BYDAY=SU", "2026-11-29T10:00", "2026-11-29T10:00"))

    @Test fun firstMondayOfTheMonth() =
        assertEquals(t("2027-01-04T09:00"), next("FREQ=MONTHLY;BYDAY=1MO", "2026-12-07T09:00", "2026-12-07T09:00"))

    @Test fun monthDay() = assertEquals(t("2027-01-01T09:00"), next("FREQ=MONTHLY;BYMONTHDAY=1", "2026-12-01T09:00", "2026-12-01T09:00"))

    // "every 31st": months without a 31st → last day.
    @Test fun lastDayWhenTheMonthIsShort() {
        val rule = "FREQ=MONTHLY;BYMONTHDAY=28,29,30,31;BYSETPOS=-1"
        assertEquals(t("2026-11-30T09:00"), next(rule, "2026-11-30T09:00", "2026-11-25T14:00"))
        assertEquals(t("2026-12-31T09:00"), next(rule, "2026-11-30T09:00", "2026-11-30T09:00"))
        assertEquals(t("2027-02-28T09:00"), next(rule, "2026-11-30T09:00", "2027-01-31T09:00"))
    }

    @Test fun everyThreeMonths() = assertEquals(t("2027-02-25T09:00"), next("FREQ=MONTHLY;INTERVAL=3", "2026-11-25T09:00", "2026-11-25T09:00"))

    @Test fun yearly() = assertEquals(t("2027-11-25T09:00"), next("FREQ=YEARLY", "2026-11-25T09:00", "2026-11-25T09:00"))

    @Test fun hourlyUntil() {
        val rule = "FREQ=HOURLY;INTERVAL=3;UNTIL=20261125T210000"
        assertEquals(t("2026-11-25T18:00"), next(rule, "2026-11-25T15:00", "2026-11-25T15:00"))
        assertEquals(t("2026-11-25T21:00"), next(rule, "2026-11-25T15:00", "2026-11-25T18:00"))
        assertNull(next(rule, "2026-11-25T15:00", "2026-11-25T21:00"))
    }

    @Test fun firstOccurrenceIsTheStartItself() =
        assertEquals(t("2026-11-26T08:00"), next("FREQ=DAILY", "2026-11-26T08:00", "2026-11-25T14:00"))

    @Test fun labels() {
        assertEquals("Every day", describe("FREQ=DAILY"))
        assertEquals("Weekdays", describe("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"))
        assertEquals("Every 2 weeks on Sun", describe("FREQ=WEEKLY;INTERVAL=2;BYDAY=SU"))
        assertEquals("Every month", describe("FREQ=MONTHLY"))
    }

    @Test fun buildsRulesFromTheEditor() {
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TH", rule(Freq.WEEKLY, 1, setOf(java.time.DayOfWeek.THURSDAY, java.time.DayOfWeek.MONDAY)))
        assertEquals("FREQ=DAILY;INTERVAL=3", rule(Freq.DAILY, 3, emptySet()))
    }
}
