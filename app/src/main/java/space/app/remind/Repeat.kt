package space.app.remind

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * The RRULE subset the app writes (RFC 5545, no DTSTART): FREQ HOURLY/DAILY/WEEKLY/MONTHLY/YEARLY,
 * INTERVAL, BYDAY (MO or 1MO), BYMONTHDAY, BYHOUR, BYSETPOS=-1 and UNTIL (local time).
 */
private class Rule(text: String) {
    val parts = text.split(';').filter { '=' in it }.associate { it.substringBefore('=').uppercase() to it.substringAfter('=') }
    val freq = Freq.valueOf(parts.getValue("FREQ").uppercase())
    val interval = parts["INTERVAL"]?.toInt() ?: 1
    val byDay = parts["BYDAY"]?.split(',').orEmpty().map { d -> d.dropLast(2).toIntOrNull() to day(d.takeLast(2)) }
    val byMonthDay = parts["BYMONTHDAY"]?.split(',').orEmpty().map { it.toInt() }
    val byHour = parts["BYHOUR"]?.split(',').orEmpty().map { it.toInt() }
    val lastOnly = parts["BYSETPOS"] == "-1"
    val until = parts["UNTIL"]?.let { LocalDateTime.parse(it.removeSuffix("Z"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")) }
}

enum class Freq { HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY }

private val dayCodes = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
private fun day(code: String) = DayOfWeek.of(dayCodes.indexOf(code.uppercase()) + 1)
private fun code(day: DayOfWeek) = dayCodes[day.value - 1]

/** The first occurrence of [rule] starting at [start] that is strictly after [after], or null when it has ended. */
fun nextOccurrence(rule: String, start: LocalDateTime, after: LocalDateTime): LocalDateTime? {
    val r = Rule(rule)
    for (period in 0 until 100_000L) {
        val candidates = occurrencesIn(r, start, period * r.interval)
        for (c in candidates.sorted()) {
            if (r.until != null && c > r.until) return null
            if (c >= start && c > after) return c
        }
    }
    return null
}

/** All occurrences in the period [offset] periods after the one holding [start]. */
private fun occurrencesIn(r: Rule, start: LocalDateTime, offset: Long): List<LocalDateTime> {
    val time = start.toLocalTime()
    fun at(date: LocalDate): List<LocalDateTime> =
        if (r.byHour.isEmpty()) listOf(date.atTime(time)) else r.byHour.map { date.atTime(LocalTime.of(it, time.minute)) }

    return when (r.freq) {
        Freq.HOURLY -> listOf(start.plusHours(offset))
        Freq.DAILY -> at(start.toLocalDate().plusDays(offset))
        Freq.WEEKLY -> {
            val monday = start.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset)
            val days = r.byDay.map { it.second }.ifEmpty { listOf(start.dayOfWeek) }
            days.flatMap { at(monday.plusDays(it.value - 1L)) }
        }
        Freq.MONTHLY -> {
            val month = start.toLocalDate().withDayOfMonth(1).plusMonths(offset)
            val length = month.lengthOfMonth()
            val dates = when {
                r.byMonthDay.isNotEmpty() -> r.byMonthDay.filter { it <= length }.map { month.withDayOfMonth(it) }
                r.byDay.isNotEmpty() -> r.byDay.mapNotNull { (n, d) ->
                    month.with(if ((n ?: 1) < 0) TemporalAdjusters.lastInMonth(d) else TemporalAdjusters.dayOfWeekInMonth(n ?: 1, d))
                        .takeIf { it.month == month.month }
                }
                start.dayOfMonth <= length -> listOf(month.withDayOfMonth(start.dayOfMonth))
                else -> emptyList()
            }
            val picked = if (r.lastOnly) listOfNotNull(dates.maxOrNull()) else dates
            picked.flatMap { at(it) }
        }
        Freq.YEARLY -> {
            val year = start.toLocalDate().plusYears(offset)
            if (year.dayOfMonth == start.dayOfMonth) at(year) else emptyList() // 29 Feb only in leap years
        }
    }
}

/** The editor's repeat choices as a rule. */
fun rule(freq: Freq, interval: Int, days: Set<DayOfWeek>): String = buildList {
    add("FREQ=${freq.name}")
    if (interval > 1) add("INTERVAL=$interval")
    if (freq == Freq.WEEKLY && days.isNotEmpty()) add("BYDAY=" + days.sorted().joinToString(",") { code(it) })
}.joinToString(";")

/** A short label for a rule, e.g. "Every 2 weeks on Sun". */
fun describe(rule: String): String {
    val r = Rule(rule)
    val unit = when (r.freq) {
        Freq.HOURLY -> "hour"; Freq.DAILY -> "day"; Freq.WEEKLY -> "week"; Freq.MONTHLY -> "month"; Freq.YEARLY -> "year"
    }
    val days = r.byDay.map { it.second }
    if (r.freq == Freq.WEEKLY && r.interval == 1 && days.toSet() == DayOfWeek.entries.take(5).toSet()) return "Weekdays"
    val every = if (r.interval == 1) "Every $unit" else "Every ${r.interval} ${unit}s"
    val on = if (r.freq == Freq.WEEKLY && days.isNotEmpty()) {
        " on " + days.joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase).take(3) }
    } else ""
    return every + on
}
