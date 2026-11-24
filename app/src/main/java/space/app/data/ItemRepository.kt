package space.app.data

import android.content.Context
import androidx.room.useWriterConnection
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

@Singleton
class ItemRepository @Inject constructor(
    private val db: SpaceDatabase,
    @ApplicationContext context: Context,
) {
    private val dao = db.itemDao()
    private val itemsDir = File(context.filesDir, "items")

    fun observeAll(): Flow<List<Item>> = dao.observeAll()

    suspend fun search(query: String): List<Item> = dao.search(query)

    suspend fun add(type: ItemType, text: String): Long =
        dao.insert(Item(type = type, text = text.trim(), createdAt = System.currentTimeMillis()))

    /** Every file that belongs to an item (audio, images, thumbnails) lives here, so it goes with it. */
    fun filesDir(id: Long) = File(itemsDir, id.toString())

    /**
     * The only way to delete an item (CLAUDE.md). Removes its files, its row and,
     * through a trigger in the same transaction, its search entry. Then checkpoints
     * the WAL so the deleted text doesn't linger there. Add every new place an item
     * leaves a trace (Markdown file, alarms, calendar event) here.
     */
    suspend fun delete(item: Item) {
        // Files first: if we die halfway, the item is still visible and can be deleted again.
        withContext(Dispatchers.IO) { filesDir(item.id).deleteRecursively() }
        db.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA secure_delete = ON") { it.step() }
        }
        dao.delete(item)
        db.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { it.step() }
        }
    }
}
