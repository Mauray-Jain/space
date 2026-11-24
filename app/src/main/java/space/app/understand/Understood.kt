package space.app.understand

import java.time.LocalDateTime

enum class Kind { NOTE, TASK, REMINDER, EVENT, LIST, QUESTION, CONTACT, DISCARD }

/** One OCR line and its box height in px (bigger usually means a title or a name). */
data class OcrLine(val text: String, val h: Int)

/** What a capture means. Only the fields that apply to its [type] are set. */
data class Understood(
    val type: Kind,
    val title: String? = null,
    val at: LocalDateTime? = null,
    val repeat: String? = null,
    /** Shopping list items. */
    val items: List<String> = emptyList(),
    /** Other dates found (e.g. a flyer listing several), offered in the Undo/Edit card. */
    val alts: List<LocalDateTime> = emptyList(),
    /** For questions: a list to open ("shopping_list"), or text to search for. */
    val target: String? = null,
    val search: String? = null,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val links: List<String> = emptyList(),
    val name: String? = null,
    val company: String? = null,
    val job: String? = null,
)
