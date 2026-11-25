package space.app.data

import android.content.Context
import androidx.room.useWriterConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import space.app.Settings
import space.app.remind.Reminders
import space.app.sync.CalendarSync
import space.app.sync.MarkdownSync
import space.app.sync.Widgets

/** What a delete took away, kept only in memory so Undo can put it back (EDGE_CASES.md §9). */
class Deleted internal constructor(val item: Item, internal val files: Map<String, ByteArray>)

@Singleton
class ItemRepository @Inject constructor(
    private val db: SpaceDatabase,
    @ApplicationContext context: Context,
    private val reminders: Reminders,
    private val settings: Settings = Settings(context),
    private val markdown: MarkdownSync = MarkdownSync(context, settings),
    private val calendar: CalendarSync = CalendarSync(context, settings),
    private val widgets: Widgets = Widgets(context),
) {
    private val dao = db.itemDao()
    private val itemsDir = File(context.filesDir, "items")

    fun observeAll(): Flow<List<Item>> = dao.observeAll()

    suspend fun get(id: Long): Item? = dao.get(id)

    /** Saves a capture. Shopping text becomes one item per line or comma. Returns the last id. */
    suspend fun add(type: ItemType, text: String, remindAt: LocalDateTime? = null, repeat: String? = null, event: Boolean = false): Long {
        val now = System.currentTimeMillis()
        val texts = if (type == ItemType.SHOPPING) splitShoppingItems(text) else listOf(text.trim())
        val ids = texts.map { t ->
            val item = Item(type = type, text = t, createdAt = now, remindAt = remindAt, repeat = repeat, event = event)
            dao.insert(item).also { afterWrite(item.copy(id = it)) }
        }
        refreshWidgets()
        return ids.last()
    }

    suspend fun update(item: Item) {
        dao.update(item.copy(updatedAt = System.currentTimeMillis()))
        afterWrite(item)
        refreshWidgets()
    }

    /** Everything outside the database that mirrors an item: alarm, calendar event, Markdown file. */
    private suspend fun afterWrite(item: Item) = withContext(Dispatchers.IO) {
        reminders.sync(item)
        calendar.applyPendingDeletes()
        val eventId = calendar.sync(item)
        val saved = if (eventId != item.eventId) item.copy(eventId = eventId).also { dao.update(it) } else item
        markdown.write(saved)
    }

    suspend fun all(): List<Item> = dao.all()

    /** Import: saves a copy of an item from a backup under a new id; returns the id. */
    suspend fun insertCopy(item: Item): Long {
        val copy = item.copy(id = 0, eventId = null)
        val id = dao.insert(copy)
        afterWrite(copy.copy(id = id))
        return id
    }

    suspend fun refreshWidgets() = withContext(Dispatchers.IO) { widgets.refresh(dao.all()) }

    /** After picking a Markdown folder or a calendar: copy everything there. */
    suspend fun resyncAll() = dao.all().forEach { afterWrite(it) }

    /** Alarms don't survive a reboot or an app update; set them all again. */
    suspend fun rearmAll() {
        dao.withReminders().forEach { reminders.sync(it) }
        refreshWidgets()
    }

    /**
     * Typo-tolerant search (PLAN.md §2): every word must appear; under 3 letters uses LIKE;
     * if nothing matches, items sharing enough 3-letter pieces, best first.
     */
    suspend fun search(query: String): List<Item> {
        val words = query.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        if (words.any { it.length < 3 }) {
            val escaped = words.maxBy { it.length }.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            return dao.like(escaped).filter { item -> words.all { item.searchable.contains(it, ignoreCase = true) } }
        }
        val exact = dao.match(words.joinToString(" ") { quote(it) })
        if (exact.isNotEmpty()) return exact
        val pieces = words.flatMap { trigrams(it) }.distinct()
        return rankByTrigrams(query, dao.match(pieces.joinToString(" OR ") { quote(it) }))
    }

    // An FTS5 string: operators and quotes inside it are just text.
    private fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    /** Every file that belongs to an item (audio, images, thumbnails) lives here, so it goes with it. */
    fun filesDir(id: Long) = File(itemsDir, id.toString())

    /**
     * The only way to delete an item (CLAUDE.md). Removes its files, its row and,
     * through a trigger in the same transaction, its search entry. Then checkpoints
     * the WAL so the deleted text doesn't linger there. Also cancels its alarm and
     * notifications, deletes its calendar event (or remembers to, if sync is off) and its
     * Markdown file. Add every new place an item leaves a trace here.
     */
    suspend fun delete(item: Item): Deleted {
        reminders.cancelAll(item.id)
        withContext(Dispatchers.IO) {
            calendar.delete(item)
            markdown.delete(item.id)
        }
        // Files first: if we die halfway, the item is still visible and can be deleted again.
        val files = withContext(Dispatchers.IO) {
            val dir = filesDir(item.id)
            val bytes = dir.walkTopDown().filter { it.isFile }.associate { it.relativeTo(dir).path to it.readBytes() }
            dir.deleteRecursively()
            bytes
        }
        db.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA secure_delete = ON") { it.step() }
        }
        dao.delete(item)
        db.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { it.step() }
        }
        refreshWidgets()
        return Deleted(item, files)
    }

    /** Undo: puts back exactly what [delete] removed. */
    suspend fun restore(deleted: Deleted) {
        // The calendar event was deleted, so Undo makes a new one.
        val item = deleted.item.copy(eventId = null)
        dao.insert(item)
        afterWrite(item)
        refreshWidgets()
        withContext(Dispatchers.IO) {
            deleted.files.forEach { (path, bytes) ->
                File(filesDir(deleted.item.id), path).apply { parentFile?.mkdirs() }.writeBytes(bytes)
            }
        }
    }
}
