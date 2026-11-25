package space.app.understand

import com.google.i18n.phonenumbers.PhoneNumberUtil
import java.time.LocalDateTime
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

private val I = RegexOption.IGNORE_CASE

/**
 * What a capture means (PLAN.md §2). MiniLM ([Understander]) decides the kind and tags the title,
 * list items and search words; the date code (findWhen, testdata/README.md rules) turns the time
 * words into a date. Text cleanup and screenshots don't use the model.
 */
class Understander(model: ByteArray, vocab: List<String>) : AutoCloseable {
    private val tagger = IntentTagger(model, vocab)

    fun understand(input: String, now: LocalDateTime, locale: Locale = Locale.getDefault(), ocr: List<OcrLine>? = null): List<Understood> {
        val extracted = details(input, locale)
        if (ocr != null || '\n' in input) return listOf(understandScreen(input, now, locale, ocr))

        val text = applyCorrections(normalizeNumbers(stripFillers(input))).trim()
        if (text.isBlank() || text.none { it.isLetterOrDigit() }) return listOf(Understood(Kind.DISCARD))

        val read = tagger.read(text)
        fun span(tag: String) = read.stretch(tag).trim().trim(',', '.', '!', '?', ';', ':', ' ')
        val found = findWhen(text, now, dayFirst(locale))
        val title = span("TITLE").ifEmpty { tidy(found.rest) }
        return listOf(
            when (read.intent) {
                "question_list" -> Understood(Kind.QUESTION, target = "shopping_list")
                "question_search" -> Understood(Kind.QUESTION, search = span("SEARCH").ifEmpty { text.trimEnd('?') })
                "list" -> read.spans("ITEM").map { it.trim(',', '.', ' ') }.filter { it.isNotEmpty() }
                    .takeIf { it.isNotEmpty() }?.let { Understood(Kind.LIST, items = it) } ?: extracted
                // A reminder with no time is a task; an appointment with no time is just a note (README rule 8).
                "reminder" -> if (found.at != null) Understood(Kind.REMINDER, title = title, at = found.at, repeat = found.repeat) else Understood(Kind.TASK, title = title)
                "event" -> if (found.at != null) Understood(Kind.EVENT, title = title, at = found.at, repeat = found.repeat, alts = found.alts) else extracted
                "task" -> Understood(Kind.TASK, title = title)
                else -> extracted
            },
        )
    }

    override fun close() = tagger.close()
}

private fun tidy(text: String) = text.replace(Regex("""\s+"""), " ").trim().trim(',', '.', '!', '?', ';', ' ')

/** Phones, emails and links, kept on whatever the capture becomes. */
private fun details(input: String, locale: Locale) =
    Understood(Kind.NOTE, phones = phones(input, locale.country.ifEmpty { "IN" }), emails = emails(input), links = links(input))

/** Screenshots and photos (no model): an event if a future date is shown, a contact if it's a card, else a note. */
fun understandScreen(input: String, now: LocalDateTime, locale: Locale = Locale.getDefault(), ocr: List<OcrLine>? = null): Understood =
    fromScreen(input, ocr, now, locale, details(input, locale))

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
