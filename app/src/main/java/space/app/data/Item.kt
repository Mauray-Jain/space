package space.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ItemType { NOTE, TASK, SHOPPING }

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ItemType,
    /** Everything you wrote, including #tags and "- [ ] subtask" lines. */
    val text: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = createdAt,
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0") val done: Boolean = false,
)
