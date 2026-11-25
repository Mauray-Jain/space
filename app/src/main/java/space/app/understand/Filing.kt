package space.app.understand

import java.time.LocalDateTime
import space.app.data.ItemType
import space.app.data.tags
import space.app.data.withTag
import space.app.remind.describe

/** An item about to be saved. */
data class Draft(val type: ItemType, val text: String, val remindAt: LocalDateTime? = null, val repeat: String? = null)

/**
 * Auto-filing: what a capture becomes. Events are saved as reminders until calendar sync
 * (milestone 7). Questions and discards save nothing. #tags in [original] stay with the item.
 */
fun drafts(u: Understood, original: String): List<Draft> {
    fun keepTags(title: String) = tags(original).fold(title) { t, tag -> withTag(t, tag) }
    return when (u.type) {
        Kind.REMINDER, Kind.EVENT -> listOf(Draft(ItemType.REMINDER, keepTags(u.title.orEmpty().ifBlank { original.trim() }), u.at, u.repeat))
        Kind.TASK -> listOf(Draft(ItemType.TASK, keepTags(u.title.orEmpty().ifBlank { original.trim() })))
        Kind.LIST -> u.items.map { Draft(ItemType.SHOPPING, it) }
        Kind.NOTE, Kind.CONTACT -> listOf(Draft(ItemType.NOTE, original.trim()))
        Kind.QUESTION, Kind.DISCARD -> emptyList()
    }
}

/** A one-line description of what a capture will become, for the quick-entry sheet. */
fun preview(u: Understood, formatTime: (LocalDateTime) -> String): String = when (u.type) {
    Kind.REMINDER, Kind.EVENT -> listOfNotNull("Reminder", u.title, u.at?.let(formatTime), u.repeat?.let { describe(it) }).joinToString(" · ")
    Kind.TASK -> "Task · ${u.title}"
    Kind.LIST -> "Shopping · ${u.items.joinToString(", ")}"
    Kind.QUESTION -> if (u.target == "shopping_list") "Opens your shopping list" else "Searches for “${u.search}”"
    Kind.NOTE, Kind.CONTACT -> "Saved as a note"
    Kind.DISCARD -> ""
}

/** Saves a capture from the quick-entry sheet: the automatic reading if it was kept, else exactly what was picked. */
@javax.inject.Singleton
class Filer @javax.inject.Inject constructor(
    private val repo: space.app.data.ItemRepository,
    private val screenshots: space.app.capture.Screenshots,
) {
    /**
     * [audio] (a voice capture) and [screenshot] are kept in the first item's folder, so they go
     * with it on delete; [source] (the page's link) is added to its text. A screenshot is then
     * read by one short job (OCR, QR, labels).
     */
    suspend fun file(
        type: ItemType, text: String, remindAt: LocalDateTime?, understood: Understood?,
        audio: FloatArray? = null, screenshot: java.io.File? = null, source: String? = null, app: String? = null,
    ): List<Long> {
        val ids = if (understood != null) {
            drafts(understood, text).map { repo.add(it.type, it.text, it.remindAt, it.repeat) }
        } else {
            listOf(repo.add(type, text, remindAt))
        }
        val first = ids.firstOrNull() ?: return ids
        if (source != null) repo.get(first)?.let { if (source !in it.text) repo.update(it.copy(text = it.text + "\n" + source)) }
        if (screenshot != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val dir = repo.filesDir(first).apply { mkdirs() }
                screenshot.copyTo(java.io.File(dir, space.app.capture.Screenshots.FILE), overwrite = true)
                screenshot.delete()
            }
            screenshots.processLater(first, app)
        }
        if (audio != null) ids.firstOrNull()?.let { id ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val dir = repo.filesDir(id).apply { mkdirs() }
                java.io.File(dir, RECORDING).outputStream().use { space.app.voice.writeWav(audio, it) }
            }
        }
        return ids
    }

    companion object {
        const val RECORDING = "recording.wav"
    }
}
