package space.app

import android.content.Context
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import space.app.data.ItemRepository
import space.app.data.ItemType
import space.app.data.SpaceDatabase
import space.app.remind.Reminders

// Items saved by the first version must survive every upgrade, still searchable.
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "migration-test.db"

    @After
    fun clean() {
        context.deleteDatabase(name)
    }

    @Test
    fun v1ToLatestKeepsItemsAndSearch() = runBlocking {
        context.deleteDatabase(name)
        // The database exactly as version 1 created it (app/schemas/.../1.json).
        BundledSQLiteDriver().open(context.getDatabasePath(name).path).apply {
            execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, type TEXT NOT NULL, text TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            V1_SEARCH_INDEX.forEach { execSQL(it) }
            execSQL("INSERT INTO items (id, type, text, createdAt) VALUES (1, 'TASK', 'call the bank', 42)")
            execSQL("PRAGMA user_version = 1")
            close()
        }

        val db = SpaceDatabase.build(context, name)
        val repo = ItemRepository(db, context, Reminders(context, Settings(context)))
        val item = repo.get(1)!!
        assertEquals("call the bank", item.text)
        assertEquals(ItemType.TASK, item.type)
        assertEquals(42L, item.updatedAt)
        assertFalse(item.pinned || item.archived || item.done || item.notified)
        assertEquals(null, item.ocrText)
        assertEquals(listOf(1L), repo.search("bank").map { it.id })

        // The search triggers were rebuilt and now cover screenshot text too.
        repo.update(item.copy(text = "call the dentist", ocrText = "Smile Clinic"))
        assertEquals(listOf(1L), repo.search("dentist").map { it.id })
        assertEquals(listOf(1L), repo.search("clinic").map { it.id })
        db.close()
    }
}

/** The search index exactly as version 1 created it. */
private val V1_SEARCH_INDEX = listOf(
    "CREATE VIRTUAL TABLE item_fts USING fts5(text, content='items', content_rowid='id', tokenize='trigram')",
    "INSERT INTO item_fts(item_fts, rank) VALUES('secure-delete', 1)",
    "CREATE TRIGGER items_ai AFTER INSERT ON items BEGIN INSERT INTO item_fts(rowid, text) VALUES (new.id, new.text); END",
    "CREATE TRIGGER items_ad AFTER DELETE ON items BEGIN INSERT INTO item_fts(item_fts, rowid, text) VALUES ('delete', old.id, old.text); END",
    "CREATE TRIGGER items_au AFTER UPDATE ON items BEGIN INSERT INTO item_fts(item_fts, rowid, text) VALUES ('delete', old.id, old.text); " +
        "INSERT INTO item_fts(rowid, text) VALUES (new.id, new.text); END",
)
