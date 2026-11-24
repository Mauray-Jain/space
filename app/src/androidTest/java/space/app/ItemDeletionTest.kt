package space.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import space.app.data.ItemRepository
import space.app.data.ItemType
import space.app.data.SpaceDatabase

@RunWith(AndroidJUnit4::class)
class ItemDeletionTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "test-${UUID.randomUUID()}.db"
    private lateinit var db: SpaceDatabase
    private lateinit var repo: ItemRepository

    @Before
    fun open() {
        db = SpaceDatabase.build(context, dbName)
        repo = ItemRepository(db, context)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase(dbName)
    }

    // PLAN.md §7: the bundled driver must have FTS5 with the trigram tokenizer.
    @Test
    fun searchFindsPartOfAWord() = runBlocking {
        repo.add(ItemType.NOTE, "dentist appointment")
        assertEquals(listOf("dentist appointment"), repo.search("dent").map { it.text })
    }

    // EDGE_CASES.md §9: one delete path removes the row, the FTS entry and the item's files.
    @Test
    fun deleteRemovesRowSearchEntryAndFiles() = runBlocking {
        val id = repo.add(ItemType.NOTE, "wifi password hunter2")
        val dir = repo.filesDir(id).apply { mkdirs() }
        File(dir, "image.png").writeText("pixels")
        val item = repo.observeAll().first().single()

        repo.delete(item)

        assertTrue(repo.observeAll().first().isEmpty())
        assertTrue(repo.search("hunter2").isEmpty())
        assertFalse(dir.exists())
    }

    // EDGE_CASES.md §9: Undo is held in memory only, and brings back the row, search entry and files.
    @Test
    fun undoRestoresWhatWasDeleted() = runBlocking {
        val id = repo.add(ItemType.NOTE, "parking level 3")
        File(repo.filesDir(id).apply { mkdirs() }, "photo.jpg").writeText("pixels")
        val deleted = repo.delete(repo.observeAll().first().single())

        repo.restore(deleted)

        assertEquals(listOf("parking level 3"), repo.search("parking").map { it.text })
        assertEquals("pixels", File(repo.filesDir(id), "photo.jpg").readText())
    }

    // EDGE_CASES.md §9: deleted text must not linger in database pages or the WAL file.
    @Test
    fun deletedTextIsNotLeftInDatabaseFiles() = runBlocking {
        val secret = "zqxwv-secret-${UUID.randomUUID()}"
        repo.add(ItemType.NOTE, "keep this one")
        repo.add(ItemType.NOTE, secret)
        val item = repo.observeAll().first().single { it.text == secret }

        repo.delete(item)

        val dbFile = context.getDatabasePath(dbName)
        for (file in listOf(dbFile, File("${dbFile.path}-wal"))) {
            if (!file.exists()) continue
            val bytes = String(file.readBytes(), Charsets.ISO_8859_1)
            assertFalse("deleted text found in ${file.name}", "zqxwv" in bytes)
        }
    }
}
