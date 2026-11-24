package space.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.SkipQueryVerification
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Insert
    suspend fun insert(item: Item): Long

    @Query("SELECT * FROM items ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<Item>>

    // item_fts is created by hand in SpaceDatabase (Room doesn't know FTS5), hence no verification.
    @SkipQueryVerification
    @Query("SELECT items.* FROM items JOIN item_fts ON item_fts.rowid = items.id WHERE item_fts MATCH :query")
    suspend fun search(query: String): List<Item>

    // Only ItemRepository.delete may call this.
    @Delete
    suspend fun delete(item: Item)
}
