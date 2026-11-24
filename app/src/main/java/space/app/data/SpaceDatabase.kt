package space.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [Item::class],
    version = SpaceDatabase.VERSION,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = SpaceDatabase.V1to2::class),
        AutoMigration(from = 2, to = 3),
    ],
)
@TypeConverters(Converters::class)
abstract class SpaceDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    class V1to2 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
            connection.execSQL("UPDATE items SET updatedAt = createdAt")
        }
    }

    companion object {
        const val VERSION = 3

        // Bundled driver: Android 12's own SQLite has no FTS5 trigram tokenizer.
        fun build(context: Context, name: String = "space.db"): SpaceDatabase =
            Room.databaseBuilder<SpaceDatabase>(context, name)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addCallback(Setup)
                .build()
    }
}

class Converters {
    @TypeConverter fun fromTime(value: LocalDateTime?): String? = value?.toString()
    @TypeConverter fun toTime(value: String?): LocalDateTime? = value?.let(LocalDateTime::parse)
}

private object Setup : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) = SEARCH_INDEX_SQL.forEach { connection.execSQL(it) }
}

/**
 * Search index over items.text. External content, so the text isn't stored twice. Deleted
 * entries are really removed from the index (secure-delete), and triggers keep it in the same
 * transaction as the row change.
 */
val SEARCH_INDEX_SQL = listOf(
    "CREATE VIRTUAL TABLE item_fts USING fts5(text, content='items', content_rowid='id', tokenize='trigram')",
    "INSERT INTO item_fts(item_fts, rank) VALUES('secure-delete', 1)",
    "CREATE TRIGGER items_ai AFTER INSERT ON items BEGIN " +
        "INSERT INTO item_fts(rowid, text) VALUES (new.id, new.text); END",
    "CREATE TRIGGER items_ad AFTER DELETE ON items BEGIN " +
        "INSERT INTO item_fts(item_fts, rowid, text) VALUES ('delete', old.id, old.text); END",
    "CREATE TRIGGER items_au AFTER UPDATE ON items BEGIN " +
        "INSERT INTO item_fts(item_fts, rowid, text) VALUES ('delete', old.id, old.text); " +
        "INSERT INTO item_fts(rowid, text) VALUES (new.id, new.text); END",
)
