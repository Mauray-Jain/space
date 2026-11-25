package space.app.sync

import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import space.app.data.Item
import space.app.data.tags

/** "call-the-bank-12.md": readable, and the id keeps it unique and findable for updates and deletes. */
fun fileName(item: Item): String {
    val slug = item.text.lineSequence().first().lowercase()
        .replace(Regex("""[^a-z0-9]+"""), "-").trim('-').take(60).trim('-')
    return "${slug.ifEmpty { "item" }}-${item.id}.md"
}

/** An item as a Markdown file with front matter (Obsidian-friendly). */
fun markdown(item: Item, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
    appendLine("---")
    appendLine("type: ${item.type.name.lowercase()}")
    appendLine("created: ${Instant.ofEpochMilli(item.createdAt).atZone(zone).truncatedTo(ChronoUnit.MINUTES).toOffsetDateTime()}")
    tags(item.text).takeIf { it.isNotEmpty() }?.let { appendLine("tags: [${it.joinToString(", ")}]") }
    item.remindAt?.let { appendLine("remind: $it") }
    item.repeat?.let { appendLine("repeat: $it") }
    if (item.done) appendLine("done: true")
    if (item.pinned) appendLine("pinned: true")
    if (item.archived) appendLine("archived: true")
    appendLine("---")
    appendLine(item.text)
    item.ocrText?.let { text ->
        appendLine()
        appendLine("> Screenshot text:")
        text.lines().forEach { appendLine("> $it") }
    }
}
