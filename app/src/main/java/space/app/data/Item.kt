package space.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

enum class ItemType { NOTE, TASK, SHOPPING, REMINDER }

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
    /** Local time, no zone: "6 pm" stays 6 pm in the phone's current zone (EDGE_CASES.md §1). For repeats, the next occurrence. */
    val remindAt: LocalDateTime? = null,
    /** RRULE without DTSTART, e.g. "FREQ=WEEKLY;BYDAY=MO,TH". */
    val repeat: String? = null,
    val snoozedUntil: LocalDateTime? = null,
    /** A one-off reminder that has already fired. */
    @ColumnInfo(defaultValue = "0") val notified: Boolean = false,
    /** Text read from the item's screenshot or photo, so it can be searched (EDGE_CASES.md §10). */
    val ocrText: String? = null,
)
