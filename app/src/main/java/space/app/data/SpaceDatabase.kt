package space.app.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [Item::class],
    version = SpaceDatabase.VERSION,
    autoMigrations = [AutoMigration(from = 1, to = 2, spec = SpaceDatabase.V1to2::class)],
)
abstract class SpaceDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao

    class V1to2 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
            connection.execSQL("UPDATE items SET updatedAt = createdAt")
        }
    }

    companion object {
        const val VERSION = 2

        // Bundled driver: Android 12's own SQLite has no FTS5 trigram tokenizer.
        fun build(context: Context, name: String = "space.db"): SpaceDatabase =
            Room.databaseBuilder<SpaceDatabase>(context, name)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .addCallback(Setup)
                .build()
    }
}

private object Setup : RoomDatabase.Callback() {
    override fun onCreate(connection: SQLiteConnection) {
        // Search index over items.text. External content: the text isn't stored twice.
        connection.execSQL(
            "CREATE VIRTUAL TABLE item_fts USING fts5(text, content='items', content_rowid='id', tokenize='trigram')"
        )
        // Deleted entries are really removed from the index, not just marked as deleted.
        connection.execSQL("INSERT INTO item_fts(item_fts, rank) VALUES('secure-delete', 1)")
        // Triggers keep the index in the same transaction as the row change.
        connection.execSQL(
            "CREATE TRIGGER items_ai AFTER INSERT ON items BEGIN " +
                "INSERT INTO item_fts(rowid, text) VALUES (new.id, new.text); END"
        )
        connection.execSQL(
            "CREATE TRIGGER items_ad AFTER DELETE ON items BEGIN " +
                "INSERT INTO item_fts(item_fts, rowid, text) VALUES ('delete', old.id, old.text); END"
        )
        connection.execSQL(
            "CREATE TRIGGER items_au AFTER UPDATE ON items BEGIN " +
                "INSERT INTO item_fts(item_fts, rowid, text) VALUES ('delete', old.id, old.text); " +
                "INSERT INTO item_fts(rowid, text) VALUES (new.id, new.text); END"
        )
    }
}
