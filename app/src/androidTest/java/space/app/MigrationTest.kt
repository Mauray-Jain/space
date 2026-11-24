package space.app

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import space.app.data.SpaceDatabase

// Items saved by an older version must survive the upgrade.
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = instrumentation,
        file = instrumentation.targetContext.getDatabasePath("migration-test.db"),
        driver = BundledSQLiteDriver(),
        databaseClass = SpaceDatabase::class,
    )

    @Test
    fun v1ToLatestKeepsItems() {
        helper.createDatabase(1).apply {
            execSQL("INSERT INTO items (id, type, text, createdAt) VALUES (1, 'TASK', 'call the bank', 42)")
            close()
        }
        val db = helper.runMigrationsAndValidate(SpaceDatabase.VERSION)
        db.prepare("SELECT text, type, pinned, archived, done, updatedAt FROM items WHERE id = 1").use {
            it.step()
            assertEquals("call the bank", it.getText(0))
            assertEquals("TASK", it.getText(1))
            assertEquals(0L, it.getLong(2))
            assertEquals(0L, it.getLong(3))
            assertEquals(0L, it.getLong(4))
            assertEquals(42L, it.getLong(5))
        }
        db.close()
    }
}
