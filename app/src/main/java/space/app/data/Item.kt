package space.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ItemType { NOTE, TASK }

@Entity(tableName = "items")
data class Item(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: ItemType,
    val text: String,
    val createdAt: Long,
)
