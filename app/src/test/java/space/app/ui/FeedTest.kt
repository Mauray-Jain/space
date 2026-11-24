package space.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import space.app.data.Item
import space.app.data.ItemType

class FeedTest {
    private val note = Item(id = 1, type = ItemType.NOTE, text = "wifi #home", createdAt = 1, updatedAt = 5)
    private val task = Item(id = 2, type = ItemType.TASK, text = "call bank", createdAt = 2, updatedAt = 2)
    private val pinned = Item(id = 3, type = ItemType.NOTE, text = "old pinned", createdAt = 0, pinned = true)
    private val archived = Item(id = 4, type = ItemType.NOTE, text = "gone #home", createdAt = 3, archived = true)
    private val milk = Item(id = 5, type = ItemType.SHOPPING, text = "milk", createdAt = 4)
    private val all = listOf(note, task, pinned, archived, milk)

    @Test
    fun allShowsPinnedFirstThenNewestAndHidesArchivedAndShopping() =
        assertEquals(listOf(3L, 2L, 1L), feed(all, Filter.ALL, tag = null, Sort.NEWEST).map { it.id })

    @Test
    fun filtersByType() {
        assertEquals(listOf(2L), feed(all, Filter.TASKS, null, Sort.NEWEST).map { it.id })
        assertEquals(listOf(5L), feed(all, Filter.SHOPPING, null, Sort.NEWEST).map { it.id })
        assertEquals(listOf(4L), feed(all, Filter.ARCHIVED, null, Sort.NEWEST).map { it.id })
    }

    @Test
    fun filtersByTag() = assertEquals(listOf(1L), feed(all, Filter.ALL, "home", Sort.NEWEST).map { it.id })

    @Test
    fun sortsOldestAndRecentlyEdited() {
        assertEquals(listOf(3L, 1L, 2L), feed(all, Filter.ALL, null, Sort.OLDEST).map { it.id })
        assertEquals(listOf(3L, 1L, 2L), feed(all, Filter.ALL, null, Sort.EDITED).map { it.id })
    }
}
