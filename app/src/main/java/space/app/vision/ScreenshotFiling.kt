package space.app.vision

import java.time.LocalDateTime
import java.util.Locale
import space.app.data.Item
import space.app.data.ItemType
import space.app.data.withTag
import space.app.understand.Kind
import space.app.understand.OcrLine
import space.app.understand.understand

/** The text a screen capture gets until it's been read. */
const val SCREENSHOT_TITLE = "Screenshot"

private val labelRules = listOf(
    "receipt" to Regex("""\b(?:total|subtotal|invoice|receipt|order\s+#?\d+|paid|amount)\b|₹|\$\s?\d""", RegexOption.IGNORE_CASE),
    "ticket" to Regex("""\b(?:pnr|boarding|e-?ticket|seat|gate|platform|coach|berth)\b""", RegexOption.IGNORE_CASE),
    "recipe" to Regex("""\b(?:ingredients|recipe|tbsp|tsp|preheat)\b""", RegexOption.IGNORE_CASE),
)
private val chatApps = setOf("com.whatsapp", "org.telegram.messenger", "com.google.android.apps.messaging", "org.thoughtcrime.securesms", "com.instagram.android")

/** Tags for a capture from its text and the app it came from (rules, no model; PLAN.md §2). */
fun labels(text: String, app: String?): List<String> =
    labelRules.filter { (_, rule) -> rule.containsMatchIn(text) }.map { it.first } + listOfNotNull("chat".takeIf { app in chatApps })

/**
 * What a screen capture becomes once it's been read. A flyer with a future date becomes a
 * reminder (EDGE_CASES.md §6) unless you wrote something yourself; otherwise it stays a note.
 * The read text is kept for search either way; labels become #tags and QR links are added.
 */
fun fileScreenshot(item: Item, lines: List<OcrLine>, codes: List<String>, app: String?, now: LocalDateTime, locale: Locale = Locale.getDefault()): Item {
    if (lines.isEmpty() && codes.isEmpty()) return item
    val ocrText = lines.joinToString("\n") { it.text }.ifEmpty { null }
    val untouched = item.text.lineSequence().first() == SCREENSHOT_TITLE
    val read = lines.takeIf { it.isNotEmpty() }?.let { understand(ocrText!!, now, locale, it).firstOrNull() }
    if (untouched && read != null && (read.type == Kind.EVENT || read.type == Kind.REMINDER) && read.at != null) {
        return item.copy(type = ItemType.REMINDER, text = read.title ?: lines.first().text, remindAt = read.at, ocrText = ocrText)
    }
    // An untitled capture is named by its first line of text.
    val title = if (untouched) lines.firstOrNull()?.text ?: SCREENSHOT_TITLE else item.text.lineSequence().first()
    val rest = item.text.lines().drop(1).filter { it.isNotBlank() }
    val tagged = labels(ocrText.orEmpty(), app).fold(title) { t, tag -> withTag(t, tag) }
    val links = codes.filter { it !in rest }
    return item.copy(text = (listOf(tagged) + rest + links).joinToString("\n"), ocrText = ocrText)
}
