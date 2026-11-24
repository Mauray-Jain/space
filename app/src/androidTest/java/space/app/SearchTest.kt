package space.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import space.app.data.ItemRepository
import space.app.data.ItemType
import space.app.data.SpaceDatabase

// EDGE_CASES.md §10.
@RunWith(AndroidJUnit4::class)
class SearchTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "test-${UUID.randomUUID()}.db"
    private lateinit var db: SpaceDatabase
    private lateinit var repo: ItemRepository

    @Before
    fun open() = runBlocking {
        db = SpaceDatabase.build(context, dbName)
        repo = ItemRepository(db, context)
        repo.add(ItemType.NOTE, "dentist thursday")
        repo.add(ItemType.NOTE, "wifi password hunter2")
        repo.add(ItemType.NOTE, "parent meeting")
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun search(q: String) = runBlocking { repo.search(q).map { it.text } }

    @Test fun partOfAWord() = assertEquals(listOf("dentist thursday"), search("dent"))

    @Test fun typo() = assertEquals(listOf("dentist thursday"), search("dentsit"))

    @Test fun underThreeLetters() = assertEquals(listOf("wifi password hunter2"), search("wi"))

    @Test fun severalWordsMustAllMatch() = assertEquals(listOf("wifi password hunter2"), search("hunter wifi"))

    @Test fun quotesAndOperatorsAreJustText() = assertEquals(emptyList<String>(), search("\"OR* NEAR("))
}
