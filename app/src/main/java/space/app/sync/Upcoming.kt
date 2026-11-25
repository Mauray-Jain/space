package space.app.sync

import space.app.data.Item
import space.app.data.ItemType
import space.app.remind.fireTime

/** What the home-screen widget lists: reminders still to come (soonest first), then open tasks. */
fun upcoming(items: List<Item>, limit: Int = 30): List<Item> {
    val live = items.filter { !it.archived && !it.done }
    val reminders = live.mapNotNull { item -> fireTime(item)?.let { item to it } }.sortedBy { it.second }.map { it.first }
    val tasks = live.filter { it.type == ItemType.TASK }.sortedByDescending { it.createdAt }
    return (reminders + tasks).take(limit)
}
