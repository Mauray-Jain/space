package space.app.ui

import space.app.data.Item
import space.app.data.ItemType
import space.app.data.tags

enum class Filter(val label: String) { ALL("All"), NOTES("Notes"), TASKS("Tasks"), REMINDERS("Reminders"), SHOPPING("Shopping"), ARCHIVED("Archived") }

enum class Sort(val label: String) { NEWEST("Newest"), OLDEST("Oldest"), EDITED("Recently edited") }

/** What the main screen shows: one feed, pinned first (PLAN.md §6). */
fun feed(items: List<Item>, filter: Filter, tag: String?, sort: Sort): List<Item> {
    val shown = items.filter { item ->
        val inFilter = when (filter) {
            Filter.ALL -> !item.archived && item.type != ItemType.SHOPPING
            Filter.NOTES -> !item.archived && item.type == ItemType.NOTE
            Filter.TASKS -> !item.archived && item.type == ItemType.TASK
            Filter.REMINDERS -> !item.archived && item.type == ItemType.REMINDER
            Filter.SHOPPING -> !item.archived && item.type == ItemType.SHOPPING
            Filter.ARCHIVED -> item.archived
        }
        inFilter && (tag == null || tag in tags(item.text))
    }
    val byKey = when (sort) {
        Sort.NEWEST -> compareByDescending<Item> { it.createdAt }
        Sort.OLDEST -> compareBy { it.createdAt }
        Sort.EDITED -> compareByDescending { it.updatedAt }
    }
    return shown.sortedWith(compareByDescending<Item> { it.pinned }.then(byKey).thenByDescending { it.id })
}
