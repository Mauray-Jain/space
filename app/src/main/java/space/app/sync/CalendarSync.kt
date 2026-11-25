package space.app.sync

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import space.app.Settings
import space.app.data.Item
import space.app.remind.epochMillis

/** A calendar the phone has (Google, Outlook, Proton… whatever is synced to Android). */
data class PhoneCalendar(val id: Long, val name: String, val account: String)

/**
 * Optional calendar sync through Android's calendar (PLAN.md §1). Only events are written. Each
 * item keeps the event id it created, so deleting the item deletes the event even if it was
 * edited in the calendar app meanwhile (EDGE_CASES.md §9).
 */
@Singleton
class CalendarSync @Inject constructor(@ApplicationContext private val context: Context, private val settings: Settings) {
    private val resolver get() = context.contentResolver

    private fun allowed() = context.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    private val on get() = settings.calendarId >= 0 && allowed()

    fun calendars(): List<PhoneCalendar> {
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val writable = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ${Calendars.CAL_ACCESS_CONTRIBUTOR}"
        return resolver.query(Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME), writable, null, null)
            ?.use { c -> buildList { while (c.moveToNext()) add(PhoneCalendar(c.getLong(0), c.getString(1) ?: "", c.getString(2) ?: "")) } }
            .orEmpty()
    }

    /** Writes or updates the item's event; returns the event id the item should keep. */
    fun sync(item: Item): Long? {
        if (!on) return item.eventId
        applyPendingDeletes()
        val start = item.remindAt
        if (!item.event || start == null) {
            item.eventId?.let { resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, it), null, null) }
            return null
        }
        val begin = epochMillis(start)
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, settings.calendarId)
            put(Events.TITLE, item.text.lineSequence().first())
            put(Events.DTSTART, begin)
            put(Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            if (item.repeat != null) {
                put(Events.RRULE, item.repeat); put(Events.DURATION, "PT1H"); putNull(Events.DTEND)
            } else {
                put(Events.DTEND, begin + 3_600_000); putNull(Events.RRULE); putNull(Events.DURATION)
            }
        }
        val existing = item.eventId
        if (existing != null && resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, existing), values, null, null) > 0) return existing
        return resolver.insert(Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
    }

    /** Part of the single delete path. With sync off, the delete is remembered for later. */
    fun delete(item: Item) {
        val id = item.eventId ?: return
        if (on) resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null)
        else settings.pendingEventDeletes = settings.pendingEventDeletes + id
    }

    fun applyPendingDeletes() {
        if (!on) return
        val pending = settings.pendingEventDeletes
        if (pending.isEmpty()) return
        pending.forEach { resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, it), null, null) }
        settings.pendingEventDeletes = emptySet()
    }
}
