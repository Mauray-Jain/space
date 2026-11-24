package space.app.understand

private val I = RegexOption.IGNORE_CASE

private val fillers = Regex("""\b(?:u+m+|u+h+|h+m+|erm|er|you know)\b[,.]?""", I)
private val leading = Regex("""^(?:so like|so|okay|ok|like|hey)\b[,\s]*""", I)
private val trailing = Regex("""[,\s]*\b(?:thanks|thank you|please)[.!]*$""", I)

/** Speech fillers and politeness that carry no meaning (EDGE_CASES.md §5). */
fun stripFillers(text: String): String {
    var s = text.replace(fillers, " ")
    while (true) {
        val next = s.trim().replace(leading, "")
        if (next == s.trim()) break
        s = next
    }
    return s.replace(trailing, "").replace(Regex("""\s+"""), " ").trim()
}

private val units = listOf(
    "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
    "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
).withIndex().associate { (i, w) -> w to i }
private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50)
private val ordinals = listOf(
    "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth", "eleventh", "twelfth",
    "thirteenth", "fourteenth", "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth",
).withIndex().associate { (i, w) -> w to i + 1 } + mapOf("twentieth" to 20, "thirtieth" to 30)

private fun ordinal(n: Int) = "$n" + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

private val unitWords = units.keys.joinToString("|")
private val ordinalWords = ordinals.keys.joinToString("|")
private val tensWords = tens.keys.joinToString("|")
private const val TIME_UNITS = """minutes?|mins?|hours?|hrs?|days?|weeks?|months?|years?"""

/**
 * Number words → digits ("six thirty" → "6:30", "twenty seventh" → "27th"), "p m" → "pm",
 * "five o clock" → "5:00" (EDGE_CASES.md §5).
 */
fun normalizeNumbers(text: String): String {
    var s = text
    s = s.replace(Regex("""\b($tensWords)[\s-]($ordinalWords)\b""", I)) { m ->
        ordinal(tens.getValue(m.groupValues[1].lowercase()) + ordinals.getValue(m.groupValues[2].lowercase()))
    }
    s = s.replace(Regex("""\b($tensWords)[\s-]($unitWords)\b""", I)) { m ->
        (tens.getValue(m.groupValues[1].lowercase()) + units.getValue(m.groupValues[2].lowercase())).toString()
    }
    s = s.replace(Regex("""\b($ordinalWords)\b""", I)) { m -> ordinal(ordinals.getValue(m.value.lowercase())) }
    s = s.replace(Regex("""\b($tensWords)\b""", I)) { m -> tens.getValue(m.value.lowercase()).toString() }
    // "one" is too common as a word ("the grey one"); only as a number of something or a time.
    s = s.replace(Regex("""\bone\b(?=\s+(?:$TIME_UNITS))|(?<=\b(?:at|around|about)\s)one\b""", I), "1")
    s = s.replace(Regex("""\b(${unitWords.replace("one|", "")})\b""", I)) { m -> units.getValue(m.value.lowercase()).toString() }

    s = s.replace(Regex("""(\d)\s*([ap])\.?\s?m\b\.?""", I)) { m -> "${m.groupValues[1]} ${m.groupValues[2].lowercase()}m" }
    s = s.replace(Regex("""\b(\d{1,2})\s*o'?\s?clock\b""", I), "$1:00")
    // "six thirty", "7 30" → 6:30, 7:30 (not "2 10 minute breaks").
    s = s.replace(Regex("""\b([1-9]|1[0-2]) ([0-5][0-9])\b(?!\s*(?:$TIME_UNITS|%))""", I), "$1:$2")
    return s
}

private val timeish = """\d{1,2}(?::\d{2})?(?:\s*[ap]m)?|today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday"""
private val correction = Regex(
    """\b(?:$timeish)[,\s]+(?:actually no|no actually|no wait|wait no|i mean|sorry|actually|no)[,\s]+(?=\S)""", I,
)

/** "tomorrow, actually no, Friday" / "at 5 no wait 6": the last time wins (EDGE_CASES.md §3). */
fun applyCorrections(text: String): String = text.replace(correction, "")
