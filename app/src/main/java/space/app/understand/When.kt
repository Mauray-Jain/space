package space.app.understand

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters
import space.app.remind.nextOccurrence

private val I = RegexOption.IGNORE_CASE

private const val DAY = """monday|tuesday|wednesday|thursday|friday|saturday|sunday"""
private const val MONTH = """jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?"""
private const val SHORT_DAY = """(?:mon|tue|wed|thu|fri|sat|sun)[a-z]*\.?,?"""
private const val PREP = """(?:\b(?:at|on|by|for|from|around|about|before)\s+)?"""

private fun dayOf(name: String): DayOfWeek = DayOfWeek.entries.first { name.lowercase().startsWith(it.name.lowercase().take(3)) }
private fun monthOf(name: String): Int = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    .indexOf(name.lowercase().take(3)) + 1
private val dayCode = mapOf(
    DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE", DayOfWeek.THURSDAY to "TH",
    DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
)

enum class PartOfDay(val hour: Int) { MORNING(9), NOON(12), AFTERNOON(15), EVENING(18), TONIGHT(20), NIGHT(21) }

/** A time as written: [bare] = no am/pm and not 24-hour, so the README's bare-hour rule decides. */
private data class Clock(val hour: Int, val minute: Int, val bare: Boolean)

/** What the parser found, and the text left once it was cut out. */
data class Found(
    val at: LocalDateTime?,
    val repeat: String?,
    val alts: List<LocalDateTime>,
    /** The input with every time phrase removed (same case). */
    val rest: String,
    /** A date was written but it has passed (e.g. an old flyer). */
    val past: Boolean,
)

/**
 * Finds the time in a capture and resolves it against [now], following testdata/README.md
 * ("Rules the expected values follow"). Every phrase it uses is cut out of the text.
 */
fun findWhen(input: String, now: LocalDateTime, dayFirst: Boolean): Found {
    val text = StringBuilder(input)
    fun take(regex: Regex, onMatch: (MatchResult) -> Unit) {
        regex.findAll(text).toList().forEach { m ->
            onMatch(m)
            for (i in m.range) text.setCharAt(i, ' ') // same length, so positions stay valid
        }
    }
    val today = now.toLocalDate()

    // Durations: "in 20 minutes", "in an hour and a half", "in 3 days" (days keep the time of day).
    var absolute: LocalDateTime? = null
    take(Regex("""\bin\s+(?:(an?|\d+)\s+(minutes?|mins?|hours?|hrs?|days?|weeks?|months?)(\s+and\s+a\s+half)?|(half)\s+an?\s+hour|a\s+couple\s+of\s+(minutes|hours|days))\b""", I)) { m ->
        val g = m.groupValues
        absolute = when {
            g[4].isNotEmpty() -> now.plusMinutes(30)
            g[5].isNotEmpty() -> when (g[5].lowercase().first()) { 'm' -> now.plusMinutes(2); 'h' -> now.plusHours(2); else -> now.plusDays(2) }
            else -> {
                val n = g[1].toLongOrNull() ?: 1
                val half = g[3].isNotEmpty()
                when (g[2].lowercase().first()) {
                    'm' -> if (g[2].lowercase().startsWith("mo")) now.plusMonths(n) else now.plusMinutes(n)
                    'h' -> now.plusMinutes(n * 60 + if (half) 30 else 0)
                    'd' -> now.plusDays(n)
                    else -> now.plusWeeks(n)
                }
            }
        }
    }

    // Repeats.
    var repeat: String? = null
    var repeatPart: PartOfDay? = null
    var repeatClock: Clock? = null
    var hourlyUntil: LocalTime? = null
    take(Regex("""\bevery\s+(\d+)\s+hours?(?:\s+(?:until|till)\s+(\d{1,2})(?::(\d{2}))?\s*([ap]m)?)?""", I)) { m ->
        repeat = "FREQ=HOURLY;INTERVAL=${m.groupValues[1]}"
        if (m.groupValues[2].isNotEmpty()) {
            val c = Clock(m.groupValues[2].toInt(), m.groupValues[3].toIntOrNull() ?: 0, m.groupValues[4].isEmpty())
            hourlyUntil = resolveClock(c, meridiem = m.groupValues[4], part = null, hasDay = false, now = now)
        }
    }
    take(Regex("""\bevery\s+(\d+)\s+(days|weeks|months|years)\b""", I)) { m ->
        val freq = when (m.groupValues[2].lowercase()) { "days" -> "DAILY"; "weeks" -> "WEEKLY"; "months" -> "MONTHLY"; else -> "YEARLY" }
        repeat = "FREQ=$freq;INTERVAL=${m.groupValues[1]}"
    }
    take(Regex("""\btwice\s+a\s+day\b""", I)) { repeat = "FREQ=DAILY;BYHOUR=9,21"; repeatClock = Clock(9, 0, false) }
    take(Regex("""\b(?:on\s+)?(?:every\s+)?weekdays\b""", I)) { repeat = "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR" }
    take(Regex("""\bevery\s+weekend\b""", I)) { repeat = "FREQ=WEEKLY;BYDAY=SA"; repeatClock = Clock(10, 0, false) }
    take(Regex("""\b(?:on\s+)?(?:the\s+)?(1st|2nd|3rd|4th|last)\s+($DAY)\s+of\s+(?:every|each|the)\s+month\b""", I)) { m ->
        val n = if (m.groupValues[1].lowercase() == "last") "-1" else m.groupValues[1].take(1)
        repeat = "FREQ=MONTHLY;BYDAY=$n${dayCode.getValue(dayOf(m.groupValues[2]))}"
    }
    fun monthDayRule(n: Int) = if (n >= 29) "FREQ=MONTHLY;BYMONTHDAY=${(28..n).joinToString(",")};BYSETPOS=-1" else "FREQ=MONTHLY;BYMONTHDAY=$n"
    take(Regex("""\b(?:on\s+)?(?:the\s+)?(\d{1,2})(?:st|nd|rd|th)\s+of\s+(?:every|each|the)\s+month\b""", I)) { m ->
        repeat = monthDayRule(m.groupValues[1].toInt())
    }
    take(Regex("""\b(?:on\s+)?every\s+(\d{1,2})(?:st|nd|rd|th)\b""", I)) { m -> repeat = monthDayRule(m.groupValues[1].toInt()) }
    take(Regex("""\b(?:on\s+)?every\s+(other\s+)?((?:$DAY)(?:\s*(?:,|and)\s*(?:$DAY))*)(?:\s+(morning|afternoon|evening|night))?""", I)) { m ->
        val days = Regex(DAY, I).findAll(m.groupValues[2]).map { dayOf(it.value) }.distinct().sorted().map { dayCode.getValue(it) }
        repeat = "FREQ=WEEKLY;" + (if (m.groupValues[1].isNotEmpty()) "INTERVAL=2;" else "") + "BYDAY=" + days.joinToString(",")
        if (m.groupValues[3].isNotEmpty()) repeatPart = PartOfDay.valueOf(m.groupValues[3].uppercase())
    }
    take(Regex("""\b(?:every|each)\s+(day|morning|afternoon|evening|night)\b""", I)) { m ->
        repeat = "FREQ=DAILY"
        if (m.groupValues[1].lowercase() != "day") repeatPart = PartOfDay.valueOf(m.groupValues[1].uppercase())
    }
    // "daily" at the very start describes the thing ("daily standup"), so it stays in the title.
    Regex("""\bdaily\b""", I).find(text)?.let { m ->
        repeat = "FREQ=DAILY"
        if (text.substring(0, m.range.first).isNotBlank()) for (i in m.range) text.setCharAt(i, ' ')
    }
    take(Regex("""\b(?:every\s+(week|month|year)|(weekly|monthly|yearly|annually))\b""", I)) { m ->
        repeat = when ((m.groupValues[1] + m.groupValues[2]).lowercase().take(4)) {
            "week" -> "FREQ=WEEKLY"; "mont" -> "FREQ=MONTHLY"; else -> "FREQ=YEARLY"
        }
    }

    // Dates written out. Several (a flyer) → the first, the others as alternatives.
    /** [next] = the following occurrence, used when this one has already passed. */
    data class Dated(val pos: Int, val date: LocalDate, val yearGiven: Boolean, val next: ((LocalDate) -> LocalDate)? = null)
    val dates = mutableListOf<Dated>()
    fun dated(pos: Int, day: Int, month: Int, year: String?) {
        if (month !in 1..12 || day !in 1..31) return
        val y = year?.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
        val base = runCatching { LocalDate.of(y ?: today.year, month, day) }.getOrNull() ?: return
        dates += if (y != null) Dated(pos, base, true) else Dated(pos, if (base < today) base.plusYears(1) else base, false) { it.plusYears(1) }
    }
    take(Regex("""$PREP\b(?:$SHORT_DAY\s+)?(\d{1,2})(?:st|nd|rd|th)?\s+($MONTH)\b\.?(?:,?\s+(\d{4}))?""", I)) { m ->
        dated(m.range.first, m.groupValues[1].toInt(), monthOf(m.groupValues[2]), m.groupValues[3].ifEmpty { null })
    }
    take(Regex("""$PREP\b(?:$SHORT_DAY\s+)?($MONTH)\.?\s+(\d{1,2})(?:st|nd|rd|th)?\b(?:,?\s+(\d{4}))?""", I)) { m ->
        dated(m.range.first, m.groupValues[2].toInt(), monthOf(m.groupValues[1]), m.groupValues[3].ifEmpty { null })
    }
    take(Regex("""$PREP\b(\d{1,2})[/.-](\d{1,2})(?:[/.-](\d{2,4}))?\b(?![:\d])""", I)) { m ->
        val a = m.groupValues[1].toInt()
        val b = m.groupValues[2].toInt()
        // The phone's locale decides the order; a number over 12 can only be the day.
        val (day, month) = when {
            a > 12 -> a to b
            b > 12 -> b to a
            dayFirst -> a to b
            else -> b to a
        }
        dated(m.range.first, day, month, m.groupValues[3].ifEmpty { null })
    }
    take(Regex("""$PREP\bthe\s+(\d{1,2})(?:st|nd|rd|th)\b""", I)) { m ->
        val n = m.groupValues[1].toInt()
        // The next date with that day of the month; today counts (README rule 2 rolls it over if the time has passed).
        var month = today.withDayOfMonth(1)
        while (n > month.lengthOfMonth() || month.withDayOfMonth(n) < today) month = month.plusMonths(1)
        dates += Dated(m.range.first, month.withDayOfMonth(n), false) { d ->
            var next = d.withDayOfMonth(1).plusMonths(1)
            while (n > next.lengthOfMonth()) next = next.plusMonths(1)
            next.withDayOfMonth(n)
        }
    }

    // Days said in words.
    var phraseTime: LocalTime? = null
    fun coming(day: DayOfWeek) = today.with(TemporalAdjusters.next(day)) // strictly after today
    take(Regex("""$PREP\b($DAY)\s+after\s+next\b""", I)) { m -> dates += Dated(m.range.first, coming(dayOf(m.groupValues[1])).plusWeeks(1), false) }
    take(Regex("""\bday\s+after\s+tomorrow\b""", I)) { m -> dates += Dated(m.range.first, today.plusDays(2), false) }
    take(Regex("""$PREP\btomorrow\b""", I)) { m -> dates += Dated(m.range.first, today.plusDays(1), false) }
    take(Regex("""$PREP\btoday\b""", I)) { m -> dates += Dated(m.range.first, today, false) { it.plusDays(1) } }
    take(Regex("""$PREP\b(?:next|this|coming)?\s*\b($DAY)\b""", I)) { m -> dates += Dated(m.range.first, coming(dayOf(m.groupValues[1])), false) }
    take(Regex("""$PREP\bnext\s+week\b""", I)) { m -> dates += Dated(m.range.first, coming(DayOfWeek.MONDAY), false) }
    take(Regex("""$PREP\bnext\s+month\b""", I)) { m -> dates += Dated(m.range.first, today.withDayOfMonth(1).plusMonths(1), false) }
    take(Regex("""$PREP\b(?:this\s+)?weekend\b""", I)) { m ->
        dates += Dated(m.range.first, today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)), false); phraseTime = LocalTime.of(10, 0)
    }
    take(Regex("""$PREP\b(?:the\s+)?end\s+of\s+(?:the\s+)?month\b""", I)) { m ->
        dates += Dated(m.range.first, today.with(TemporalAdjusters.lastDayOfMonth()), false)
    }
    take(Regex("""$PREP\b(?:eod|end\s+of\s+(?:the\s+)?day)\b""", I)) { m -> dates += Dated(m.range.first, today, false); phraseTime = LocalTime.of(17, 0) }
    take(Regex("""$PREP\b(?:eow|end\s+of\s+(?:the\s+)?week)\b""", I)) { m ->
        dates += Dated(m.range.first, today.with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY)), false); phraseTime = LocalTime.of(17, 0)
    }

    // Parts of the day.
    var part: PartOfDay? = repeatPart
    var midnight = false
    take(Regex("""\btonight\b""", I)) { part = PartOfDay.TONIGHT }
    take(Regex("""\b(?:this\s+|in\s+the\s+)?(morning|afternoon|evening|night)\b""", I)) { m -> part = PartOfDay.valueOf(m.groupValues[1].uppercase()) }
    take(Regex("""$PREP\b(?:noon|midday)\b""", I)) { part = PartOfDay.NOON }
    take(Regex("""$PREP\bmidnight\b""", I)) { midnight = true }
    take(Regex("""\bonwards\b""", I)) {}

    // Clock times. The first one written wins ("11 am – 2 pm" → 11 am).
    data class Timed(val pos: Int, val clock: Clock, val meridiem: String)
    val times = mutableListOf<Timed>()
    take(Regex("""$PREP\b(half|quarter|\d{1,2})\s+(past|to)\s+(\d{1,2})\b(?:\s*([ap]m))?""", I)) { m ->
        val mins = when (m.groupValues[1].lowercase()) { "half" -> 30; "quarter" -> 15; else -> m.groupValues[1].toInt() }
        var h = m.groupValues[3].toInt()
        val minute = if (m.groupValues[2].lowercase() == "past") mins else { h -= 1; 60 - mins }
        times += Timed(m.range.first, Clock((h + 12) % 12, minute, m.groupValues[4].isEmpty()), m.groupValues[4])
    }
    take(Regex("""$PREP\bhalf\s+(\d{1,2})\b(?:\s*([ap]m))?""", I)) { m ->
        times += Timed(m.range.first, Clock(m.groupValues[1].toInt(), 30, m.groupValues[2].isEmpty()), m.groupValues[2])
    }
    take(Regex("""$PREP\b(\d{1,2}):(\d{2})\b(?:\s*([ap]m)\b)?""", I)) { m ->
        val h = m.groupValues[1].toInt()
        val is24 = h >= 13 || h == 0 || m.groupValues[1].startsWith("0")
        times += Timed(m.range.first, Clock(h, m.groupValues[2].toInt(), m.groupValues[3].isEmpty() && !is24), m.groupValues[3])
    }
    take(Regex("""$PREP\b(\d{1,2})\s*([ap]m)\b""", I)) { m ->
        times += Timed(m.range.first, Clock(m.groupValues[1].toInt(), 0, false), m.groupValues[2])
    }
    take(Regex("""\b(?:at|around|about|by)\s+(?:around\s+|about\s+)?(\d{1,2})(?:ish)?\b(?![:.,]?\d|\s*(?:%|minutes?|hours?|days?|weeks?|kg|km|people|of))""", I)) { m ->
        times += Timed(m.range.first, Clock(m.groupValues[1].toInt(), 0, true), "")
    }
    take(Regex("""\b(\d{1,2})ish\b""", I)) { m -> times += Timed(m.range.first, Clock(m.groupValues[1].toInt(), 0, true), "") }

    val rest = text.toString().replace(Regex("""\s+"""), " ").trim()
    val firstDate = dates.minByOrNull { it.pos }
    val date = firstDate?.date
    val clock = times.minByOrNull { it.pos }
    val hasDay = date != null || repeat != null

    // Resolve the time of day.
    val time: LocalTime? = when {
        midnight -> LocalTime.MIDNIGHT
        clock != null -> resolveClock(clock.clock, clock.meridiem, part, hasDay, now.takeIf { date == null || date == today })
        repeatClock != null -> LocalTime.of(repeatClock!!.hour, repeatClock!!.minute)
        phraseTime != null -> phraseTime
        part != null -> LocalTime.of(part!!.hour, 0)
        else -> null
    }

    if (repeat != null) {
        val r = repeat!!
        val first = if (r.startsWith("FREQ=HOURLY")) {
            val rule = hourlyUntil?.let { "$r;UNTIL=${today.atTime(it).toString().replace("-", "").replace(":", "")}00" } ?: r
            return Found(nextOccurrence(rule, now, now), rule, emptyList(), rest, false)
        } else {
            nextOccurrence(r, (date ?: today).atTime(time ?: LocalTime.of(9, 0)), now)
        }
        return Found(first, r, emptyList(), rest, false)
    }
    absolute?.let { return Found(it, null, emptyList(), rest, false) }
    if (date == null && time == null) return Found(null, null, emptyList(), rest, false)

    var at = (date ?: today).atTime(time ?: LocalTime.of(9, 0))
    val past = at <= now
    if (past) {
        val next = firstDate?.next
        when {
            firstDate?.yearGiven == true -> return Found(null, null, emptyList(), rest, true) // an old flyer or bill
            date == null -> at = at.plusDays(1) // "at 3" when it's 4 pm, "in the morning" after 9 → tomorrow
            next != null -> at = next(date).atTime(at.toLocalTime()) // "on the 25th" on the 25th → next month
        }
    }
    val alts = dates.filter { it !== firstDate }.sortedBy { it.pos }.map { it.date.atTime(at.toLocalTime()) }
    return Found(at, null, alts, rest, false)
}

/**
 * README rule 1. Bare hours: 1–6 → pm; 12 → noon; 7–11 with a day or a repeat → am,
 * otherwise the next future occurrence. A part of the day ("7 tonight") settles am/pm.
 * [now] is given only when the time is for today, so "next future occurrence" can be checked.
 */
private fun resolveClock(c: Clock, meridiem: String, part: PartOfDay?, hasDay: Boolean, now: LocalDateTime?): LocalTime {
    val m = meridiem.lowercase()
    val h = c.hour % 24
    val hour = when {
        m == "am" -> h % 12
        m == "pm" -> h % 12 + 12
        !c.bare -> h
        part == PartOfDay.MORNING -> h % 12
        part != null -> if (h == 12) 12 else h % 12 + 12
        h == 12 -> 12
        h in 1..6 -> h + 12
        hasDay || now == null -> h
        // Next future occurrence: today am, today pm, else tomorrow am (rolled over by the caller).
        LocalTime.of(h, c.minute) > now.toLocalTime() -> h
        LocalTime.of(h + 12, c.minute) > now.toLocalTime() -> h + 12
        else -> h
    }
    return LocalTime.of(hour % 24, c.minute)
}
