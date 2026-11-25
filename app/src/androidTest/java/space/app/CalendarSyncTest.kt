package space.app

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import space.app.data.ItemRepository
import space.app.data.ItemType
import space.app.data.SpaceDatabase
import space.app.remind.Reminders

// Milestone 7, EDGE_CASES.md §9: events follow their items, deletes included.
@RunWith(AndroidJUnit4::class)
class CalendarSyncTest {
    @get:Rule
    val calendarAllowed: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "test-${UUID.randomUUID()}.db"
    private val settings = Settings(context)
    private lateinit var db: SpaceDatabase
    private lateinit var repo: ItemRepository
    private var calendarId = -1L
    private val later = LocalDateTime.of(2035, 3, 1, 15, 0)

    private fun asSyncAdapter(uri: android.net.Uri) = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, "space-test")
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()

    @Before
    fun setUp() {
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, "space-test"); put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "Space test"); put(Calendars.CALENDAR_DISPLAY_NAME, "Space test")
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER); put(Calendars.OWNER_ACCOUNT, "space-test")
            put(Calendars.VISIBLE, 1); put(Calendars.SYNC_EVENTS, 1)
        }
        calendarId = ContentUris.parseId(context.contentResolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values)!!)
        settings.calendarId = calendarId
        settings.pendingEventDeletes = emptySet()
        db = SpaceDatabase.build(context, dbName)
        repo = ItemRepository(db, context, Reminders(context, settings))
    }

    @After
    fun tearDown() {
        settings.calendarId = -1
        context.contentResolver.delete(asSyncAdapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId)), null, null)
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun eventTitle(id: Long): String? = context.contentResolver
        .query(ContentUris.withAppendedId(Events.CONTENT_URI, id), arrayOf(Events.TITLE, Events.DELETED), null, null, null)
        ?.use { c -> if (c.moveToFirst() && c.getInt(1) == 0) c.getString(0) else null }

    @Test
    fun eventIsWrittenUpdatedAndDeletedWithItsItem() = runBlocking {
        val id = repo.add(ItemType.REMINDER, "dentist", remindAt = later, event = true)
        val eventId = repo.get(id)!!.eventId
        assertNotNull(eventId)
        assertEquals("dentist", eventTitle(eventId!!))

        repo.update(repo.get(id)!!.copy(text = "dentist (Dr. Shah)"))
        assertEquals("dentist (Dr. Shah)", eventTitle(eventId))

        repo.delete(repo.get(id)!!)
        assertNull(eventTitle(eventId))
    }

    @Test
    fun plainRemindersStayOutOfTheCalendar() = runBlocking {
        val id = repo.add(ItemType.REMINDER, "call mom", remindAt = later)
        assertNull(repo.get(id)!!.eventId)
        repo.delete(repo.get(id)!!)
        Unit
    }

    // Sync off at delete time, on later: the remembered delete is applied (EDGE_CASES.md §9).
    @Test
    fun deleteWhileSyncIsOffIsAppliedLater() = runBlocking {
        val id = repo.add(ItemType.REMINDER, "plumber", remindAt = later, event = true)
        val eventId = repo.get(id)!!.eventId!!
        settings.calendarId = -1
        repo.delete(repo.get(id)!!)
        assertEquals("plumber", eventTitle(eventId))

        settings.calendarId = calendarId
        repo.add(ItemType.NOTE, "anything") // the next sync
        assertNull(eventTitle(eventId))
    }
}
