package space.app.understand

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.time.LocalDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

private val I = RegexOption.IGNORE_CASE

/**
 * Rules first (PLAN.md §2): what a capture means, following testdata/README.md
 * ("Rules the expected values follow"). Pure function, no model, no Android.
 */
fun understand(input: String, now: LocalDateTime, locale: Locale = Locale.getDefault(), ocr: List<OcrLine>? = null): List<Understood> {
    val region = locale.country.ifEmpty { "IN" }
    val phones = phones(input, region)
    val emails = emails(input)
    val links = links(input)
    val extracted = Understood(Kind.NOTE, phones = phones, emails = emails, links = links)

    if (ocr != null || '\n' in input) return listOf(fromScreen(input, ocr, now, locale, extracted))

    // 12. Discard: empty, or fillers only.
    val text = applyCorrections(normalizeNumbers(stripFillers(input))).trim().trimEnd('.', '!')
    if (text.isBlank() || text.none { it.isLetterOrDigit() }) return listOf(Understood(Kind.DISCARD))

    // 11. Long input → note, no auto-actions.
    if (text.split(Regex("""\s+""")).size > 40) return listOf(extracted)

    question(text)?.let { return listOf(it) }

    // 9. Negation guard: no auto-action. "don't forget to…" and "remember to…" are positive.
    val positive = Regex("""\b(?:don'?t|do not|never)\s+forget\s+to\b""", I)
    if (Regex("""\b(?:don'?t|dont|do not|won'?t|no need|not|never|no longer|cancel(?:led|ed)?|isn'?t|aren'?t)\b""", I)
            .containsMatchIn(text.replace(positive, ""))
    ) return listOf(extracted)

    // 14. Holidays and birthdays as the date → note, no time (no holiday calendar in v1).
    if (Regex("""\bon\s+(?:my\s+birthday|diwali|holi|christmas|eid|new year'?s?(?:\s+day|\s+eve)?)\b""", I).containsMatchIn(text)) {
        return listOf(extracted)
    }

    // 13. Location phrases → task with the phrase kept (location reminders are v2).
    if (Regex("""\bwhen\s+(?:i'?m|i\s+am|i\s+get|i\s+reach|i\s+arrive)\b""", I).containsMatchIn(text)) {
        return listOf(Understood(Kind.TASK, title = cleanTitle(stripTrigger(text))))
    }

    // A label ("Idea: …", "book recommendation: …", "landlord email: …") is a note.
    if (Regex("""^\S+(?:\s+\S+)?:\s""").containsMatchIn(text)) return listOf(extracted)

    val found = findWhen(text, now, dayFirst(locale))
    val triggered = trigger.containsMatchIn(found.rest)
    val title = cleanTitle(stripTrigger(found.rest))
    val timed = found.at != null

    if (triggered) {
        return listOf(
            if (timed) Understood(Kind.REMINDER, title = title, at = found.at, repeat = found.repeat)
            else Understood(Kind.TASK, title = title),
        )
    }
    if (!timed) shoppingList(text)?.let { return listOf(it) }

    val verbFirst = title.split(' ').firstOrNull()?.lowercase()?.trim(',') in verbs
    return listOf(
        when {
            timed && verbFirst -> Understood(Kind.REMINDER, title = title, at = found.at, repeat = found.repeat)
            timed -> Understood(Kind.EVENT, title = title, at = found.at, repeat = found.repeat, alts = found.alts)
            verbFirst -> Understood(Kind.TASK, title = title)
            else -> extracted
        },
    )
}

private val trigger = Regex(
    """\b(?:remind\s+me(?:\s+to)?|remember\s+to|(?:don'?t|do\s+not|never)\s+forget\s+to|set\s+an?\s+reminder(?:\s+for|\s+to)?)\b""", I,
)

private fun stripTrigger(text: String) = text.replace(trigger, " ")

/** Joins what's left once times and triggers are cut out into a title. */
private fun cleanTitle(text: String): String {
    var s = text.replace(Regex("""\s+"""), " ").trim()
    // Words orphaned by the cut ("remind me … to pay", "set a reminder for … to go").
    repeat(3) {
        s = s.replace(Regex("""^(?:to|and|at|on|by|for|then|,)\s+""", I), "")
            .replace(Regex("""\s+(?:at|on|by|for|to|and|,)$""", I), "")
            .trim().trim(',', '.', '!', '?', ';', ' ')
    }
    return s.replace(Regex("""\s+,"""), ",")
}

/** Imperative verbs a task or reminder starts with ("call the bank"). Anything else + a time is an event. */
private val verbs = """
    call text email mail message ping pay buy get grab pick take water send check book renew fix return clean feed submit
    log lock stretch go leave drink change put wash cook make write read finish order visit collect bring charge print sign
    schedule meet ask tell reply review prepare plan start stop walk wake study practice apply file update upload download
    install clear empty fill refill top recharge transfer withdraw deposit post ship drop deliver follow confirm see watch
    listen iron fold vacuum mop sweep throw cut trim mow repair replace try find look search learn teach help thank wish
    invite join attend register cancel pack unpack move sell rent borrow lend wrap hang set turn switch backup back
""".split(Regex("""\s+""")).filter { it.isNotEmpty() }.toSet()

// 8. Shopping: "buy/grab/get/need" + short items; "add X to the shopping list".
private val pairs = listOf("salt and pepper", "bread and butter", "mac and cheese", "fish and chips", "milk and cookies")

private fun shoppingList(text: String): Understood? {
    val m = Regex("""^(?:(?:we|i)\s+)?(?:need(?:\s+to\s+buy)?|buy|grab|get|pick\s+up)\s+(.+)$""", I).find(text)
        ?: Regex("""^(?:add|put)\s+(.+?)\s+(?:to|on|in)\s+(?:the\s+|my\s+)?(?:shopping|grocery|groceries)(?:\s+list)?$""", I).find(text)
        ?: Regex("""^(?:we'?re\s+|i'?m\s+|we\s+are\s+)?out\s+of\s+(.+)$""", I).find(text)
        ?: return null
    val what = m.groupValues[1]
    // A gift "for Sam", tyres "before the trip", "the car serviced": a task, not groceries.
    if (Regex("""\b(?:for|before|after|from)\b""", I).containsMatchIn(what) || Regex("""^(?:the|my|your|our)\b""", I).containsMatchIn(what)) return null
    var protectedText = what
    pairs.forEachIndexed { i, p -> protectedText = protectedText.replace(Regex("""\b$p\b""", I), "\u0000$i\u0000") }
    val items = protectedText.split(Regex(""",\s*(?:and\s+)?|\s+and\s+""", I))
        .map { part -> part.replace(Regex("""\u0000(\d+)\u0000""")) { pairs[it.groupValues[1].toInt()] } }
        .map { it.replace(Regex("""^some\s+""", I), "").trim().trimEnd('.', '!') }
        .filter { it.isNotEmpty() }
    if (items.isEmpty() || items.any { it.split(' ').size > 4 }) return null
    return Understood(Kind.LIST, items = items)
}

private val questionStart = Regex(
    """^(?:what'?s|whats|what|when'?s|when|where'?s|where|who|which|how|did|do|does|have|has|is|are|was|show|find|search)\b""", I,
)

/** "what's on my grocery list?" → open the list; "when is the dentist?" → search for "dentist". */
private fun question(text: String): Understood? {
    if (!questionStart.containsMatchIn(text)) return null
    if (Regex("""\b(?:shopping|grocery|groceries)\b|\bneed\s+to\s+buy\b""", I).containsMatchIn(text)) {
        return Understood(Kind.QUESTION, target = "shopping_list")
    }
    val t = text.trimEnd('?', '.', '!', ' ')
    // The thing asked about is usually after the last "the"; otherwise drop the question words.
    val afterThe = Regex("""\bthe\s+""", I).findAll(t).lastOrNull()?.let { t.substring(it.range.last + 1) }
    val search = afterThe ?: t.split(' ')
        .dropWhile { w -> w.lowercase().trim('\'') in questionWords }
        .joinToString(" ")
    return Understood(Kind.QUESTION, search = search.ifBlank { t })
}

private val questionWords = setOf(
    "what's", "whats", "what", "when's", "when", "where's", "where", "who", "which", "how", "did", "do", "does", "have", "has",
    "is", "are", "was", "show", "me", "find", "search", "i", "my", "we", "you", "a", "an",
)

/** The phone's date order ("05/06": 5 June in India, May 6 in the US). */
private fun dayFirst(locale: Locale): Boolean {
    val pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale)
    return pattern.indexOf('d') < pattern.indexOf('M')
}

// Screenshots and photos: an event if it has a future date, a contact if it's a card, else a note.
private fun fromScreen(input: String, ocr: List<OcrLine>?, now: LocalDateTime, locale: Locale, extracted: Understood): Understood {
    val lines = ocr ?: input.lines().map { OcrLine(it, 0) }
    val found = findWhen(input.replace("\n", " \n "), now, dayFirst(locale))
    if (found.at == null) {
        if (lines.size >= 3 && (extracted.phones.isNotEmpty() || extracted.emails.isNotEmpty())) return contact(lines, extracted)
        return extracted
    }
    if (Regex("""\b(?:diwali|holi|christmas|eid)\b""", I).containsMatchIn(input)) return extracted
    val title = lines.maxByOrNull { it.h }?.takeIf { it.h > 0 }?.text ?: lines.first().text
    return extracted.copy(type = Kind.EVENT, title = title.trim(), at = found.at, alts = found.alts)
}

private val companyWords = Regex("""\b(?:pvt|ltd|llp|llc|inc|corp|co|limited|technologies|solutions|clinic|hospital|labs?|studio|group|bank|foundation)\b\.?""", I)
private val jobWords = Regex(
    """\b(?:engineer|manager|director|designer|developer|founder|ceo|cto|cfo|coo|consultant|analyst|officer|lead|head|president|executive|associate|architect|intern|partner|specialist|advisor|owner)\b""", I,
)

/** Business card: name = biggest line that's not a company, job or contact detail (heuristic, EDGE_CASES.md §6). */
private fun contact(lines: List<OcrLine>, extracted: Understood): Understood {
    val details = Regex("""@|\d{3}|www\.|https?://""", I)
    val text = lines.filterNot { details.containsMatchIn(it.text) }
    val company = text.firstOrNull { companyWords.containsMatchIn(it.text) }?.text
    val job = text.firstOrNull { jobWords.containsMatchIn(it.text) && it.text != company }?.text
    val name = text.filter { it.text != company && it.text != job }.maxByOrNull { it.h }?.text
    return extracted.copy(type = Kind.CONTACT, name = name?.trim(), company = company?.trim(), job = job?.trim())
}

private val phoneUtil = PhoneNumberUtil.getInstance()

/** Phone numbers in E.164, read with the phone's region as the default. */
fun phones(text: String, region: String): List<String> =
    phoneUtil.findNumbers(text, region, PhoneNumberUtil.Leniency.VALID, Long.MAX_VALUE)
        .map { phoneUtil.format(it.number(), PhoneNumberUtil.PhoneNumberFormat.E164) }
        .distinct()

private val emailPattern = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")

fun emails(text: String): List<String> = emailPattern.findAll(text).map { it.value.trimEnd('.') }.distinct().toList()

private val linkPattern = Regex(
    """(?<![@\w.])(?:https?://[^\s]+|www\.[^\s]+|[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.(?:com|in|org|net|io|dev|co|app|me|ai)(?:/[^\s]*)?)(?![\w@])""", I,
)

/** Links, as https:// URLs ("example.com/x" → "https://example.com/x"). Email domains aren't links. */
fun links(text: String): List<String> = linkPattern.findAll(text.replace(emailPattern, " "))
    .map { it.value.trimEnd('.', ',', ')') }
    .map { if (it.startsWith("http", ignoreCase = true)) it else "https://$it" }
    .distinct().toList()
