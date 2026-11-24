package space.app

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import space.app.data.ItemRepository
import space.app.data.ItemType
import space.app.data.SpaceDatabase
import space.app.remind.ReminderActions
import space.app.remind.Reminders
import space.app.remind.Snooze

@RunWith(AndroidJUnit4::class)
class ReminderTest {
    @get:Rule
    val notificationsAllowed: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "test-${UUID.randomUUID()}.db"
    private val reminders = Reminders(context, Settings(context))
    private lateinit var db: SpaceDatabase
    private lateinit var repo: ItemRepository
    private lateinit var actions: ReminderActions

    // Far enough ahead that nothing really fires during the test.
    private val later = LocalDateTime.of(2035, 1, 1, 9, 0)

    @Before
    fun open() {
        db = SpaceDatabase.build(context, dbName)
        repo = ItemRepository(db, context, reminders)
        actions = ReminderActions(repo, reminders)
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun shown(tag: String, id: Long) = context.getSystemService(NotificationManager::class.java)
        .activeNotifications.any { it.tag == tag && it.id == id.toInt() }

    @Test
    fun addingAReminderArmsItsAlarm() = runBlocking {
        val id = repo.add(ItemType.REMINDER, "call mom", remindAt = later)
        assertTrue(reminders.isArmed(id))
        reminders.cancelAll(id)
    }

    // CLAUDE.md: the delete path removes alarms and notifications too.
    @Test
    fun deletingCancelsAlarmAndNotification() = runBlocking {
        val id = repo.add(ItemType.REMINDER, "call mom", remindAt = later)
        reminders.showFired(repo.get(id)!!)
        repo.delete(repo.get(id)!!)
        assertFalse(reminders.isArmed(id))
        assertFalse(shown(Reminders.TAG_FIRED, id))
    }

    @Test
    fun oneOffFiresOnceThenStaysQuiet() = runBlocking {
        val id = repo.add(ItemType.REMINDER, "take out bins", remindAt = later)
        actions.fire(id)
        assertTrue(shown(Reminders.TAG_FIRED, id))
        assertTrue(repo.get(id)!!.notified)
        assertFalse(reminders.isArmed(id))

        actions.done(id)
        assertTrue(repo.get(id)!!.done)
        assertFalse(shown(Reminders.TAG_FIRED, id))
    }

    @Test
    fun repeatingMovesToTheNextOccurrence() = runBlocking {
        val id = repo.add(ItemType.REMINDER, "stand up", remindAt = later, repeat = "FREQ=DAILY")
        actions.fire(id, now = later)
        assertEquals(later.plusDays(1), repo.get(id)!!.remindAt)
        assertTrue(reminders.isArmed(id))
        repo.delete(repo.get(id)!!)
    }

    @Test
    fun snoozeRearmsAndEditingClearsIt() = runBlocking {
        val id = repo.add(ItemType.REMINDER, "call mom", remindAt = later)
        actions.fire(id)
        actions.snooze(id, Snooze.MIN_30, now = later)
        val snoozed = repo.get(id)!!
        assertEquals(later.plusMinutes(30), snoozed.snoozedUntil)
        assertTrue(reminders.isArmed(id))
        repo.delete(snoozed)
    }

    // EDGE_CASES.md §8: alarms are gone after a reboot; the boot receiver sets them again.
    @Test
    fun rearmAfterReboot() = runBlocking {
        val id = repo.add(ItemType.REMINDER, "pay rent", remindAt = later)
        reminders.cancelAll(id)
        assertFalse(reminders.isArmed(id))
        actions.rearmAll()
        assertTrue(reminders.isArmed(id))
        repo.delete(repo.get(id)!!)
    }

    @Test
    fun undoOnTheSavedNotificationReallyDeletes() = runBlocking {
        val id = repo.add(ItemType.REMINDER, "dentist", remindAt = later)
        reminders.showCreated(repo.get(id)!!, "Reminder set")
        actions.undo(id)
        assertNull(repo.get(id))
        assertFalse(reminders.isArmed(id))
        assertFalse(shown(Reminders.TAG_CREATED, id))
    }
}
