package space.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.SkipQueryVerification
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Insert
    suspend fun insert(item: Item): Long

    @Update
    suspend fun update(item: Item)

    @Query("SELECT * FROM items")
    fun observeAll(): Flow<List<Item>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): Item?

    // item_fts is created by hand in SpaceDatabase (Room doesn't know FTS5), hence no verification.
    @SkipQueryVerification
    @Query("SELECT items.* FROM items JOIN item_fts ON item_fts.rowid = items.id WHERE item_fts MATCH :match ORDER BY rank")
    suspend fun match(match: String): List<Item>

    @Query("SELECT * FROM items WHERE text LIKE '%' || :text || '%' ESCAPE '\\'")
    suspend fun like(text: String): List<Item>

    // Only ItemRepository.delete may call this.
    @Delete
    suspend fun delete(item: Item)
}
