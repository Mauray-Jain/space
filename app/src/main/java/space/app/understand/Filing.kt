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
    Kind.NOTE, Kind.CONTACT -> "Note"
    Kind.DISCARD -> ""
}
