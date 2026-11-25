package space.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
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
import space.app.remind.Reminders
import space.app.sync.Backup

// Export everything to one file and read it back (PLAN.md §4).
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val names = listOf("test-${UUID.randomUUID()}.db", "test-${UUID.randomUUID()}.db")
    private lateinit var dbs: List<SpaceDatabase>
    private lateinit var from: ItemRepository
    private lateinit var to: ItemRepository

    @Before
    fun open() {
        dbs = names.map { SpaceDatabase.build(context, it) }
        from = ItemRepository(dbs[0], context, Reminders(context, Settings(context)))
        to = ItemRepository(dbs[1], context, Reminders(context, Settings(context)))
    }

    @After
    fun close() {
        dbs.forEach { it.close() }
        names.forEach { context.deleteDatabase(it) }
    }

    private suspend fun fill(): Long {
        from.add(ItemType.NOTE, "wifi password is hunter2 #home")
        val id = from.add(ItemType.REMINDER, "take my pills", remindAt = LocalDateTime.of(2035, 1, 1, 8, 0), repeat = "FREQ=DAILY")
        File(from.filesDir(id).apply { mkdirs() }, "recording.wav").writeText("audio")
        return id
    }

    @Test
    fun roundTripWithPassword() = runBlocking {
        fill()
        val out = ByteArrayOutputStream()
        Backup.export(from, out, password = "secret")
        val imported = Backup.import(to, ByteArrayInputStream(out.toByteArray()), password = "secret")

        assertEquals(2, imported)
        val items = to.all().sortedBy { it.text }
        assertEquals(listOf("take my pills", "wifi password is hunter2 #home"), items.map { it.text })
        val pills = items.first()
        assertEquals("FREQ=DAILY", pills.repeat)
        assertEquals("audio", File(to.filesDir(pills.id), "recording.wav").readText())
        to.all().forEach { to.delete(it) }
        from.all().forEach { from.delete(it) }
    }

    // Importing the same file twice skips what's already there.
    @Test
    fun importSkipsExistingItems() = runBlocking {
        fill()
        val out = ByteArrayOutputStream()
        Backup.export(from, out, password = null)
        assertEquals(2, Backup.import(to, ByteArrayInputStream(out.toByteArray()), password = null))
        assertEquals(0, Backup.import(to, ByteArrayInputStream(out.toByteArray()), password = null))
        assertEquals(2, to.all().size)
        to.all().forEach { to.delete(it) }
        from.all().forEach { from.delete(it) }
    }

    @Test
    fun wrongPasswordImportsNothing() = runBlocking {
        fill()
        val out = ByteArrayOutputStream()
        Backup.export(from, out, password = "secret")
        assertTrue(runCatching { Backup.import(to, ByteArrayInputStream(out.toByteArray()), password = "nope") }.isFailure)
        assertFalse(to.all().isNotEmpty())
        from.all().forEach { from.delete(it) }
    }
}
