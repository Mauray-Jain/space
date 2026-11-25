package space.app

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class ReminderStyle(val label: String, val description: String) {
    NOTIFICATION("Notification", "A normal notification. Respects Do Not Disturb."),
    ALARM("Alarm", "Rings until you act, full screen, through Do Not Disturb."),
}

enum class AppTheme(val label: String) { SYSTEM("Like the system"), LIGHT("Light"), DARK("Dark") }

/** App settings. Plain SharedPreferences: a few values, no library needed. */
@Singleton
class Settings @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** The Essential Key's key code, learned in Settings (it isn't documented; PLAN.md §7). -1 = not set. */
    var keyCode: Int
        get() = prefs.getInt("keyCode", -1)
        set(value) = prefs.edit { putInt("keyCode", value) }

    /** Set while Settings waits for you to press the key once. */
    var learningKey: Boolean
        get() = prefs.getBoolean("learningKey", false)
        set(value) = prefs.edit { putBoolean("learningKey", value) }

    /** Off switch for the key (some banking apps dislike accessibility services). */
    var keyEnabled: Boolean
        get() = prefs.getBoolean("keyEnabled", true)
        set(value) = prefs.edit { putBoolean("keyEnabled", value) }

    /** Folder (a document tree you picked) that notes are copied to as Markdown, one way. Null = off. */
    var markdownFolder: String?
        get() = prefs.getString("markdownFolder", null)
        set(value) = prefs.edit { putString("markdownFolder", value) }

    /** Calendar that events are written to. -1 = calendar sync off. */
    var calendarId: Long
        get() = prefs.getLong("calendarId", -1)
        set(value) = prefs.edit { putLong("calendarId", value) }

    /** Calendar events of deleted items that couldn't be deleted yet (sync was off); deleted when it's back on. */
    var pendingEventDeletes: Set<Long>
        get() = prefs.getStringSet("pendingEventDeletes", emptySet())!!.map { it.toLong() }.toSet()
        set(value) = prefs.edit { putStringSet("pendingEventDeletes", value.map { it.toString() }.toSet()) }

    /** A notification at 8 AM listing open tasks with no time (optional, PLAN.md §3). */
    var morningSummary: Boolean
        get() = prefs.getBoolean("morningSummary", false)
        set(value) = prefs.edit { putBoolean("morningSummary", value) }

    /** Light or dark regardless of the system (wishlist), or follow it. */
    var theme: AppTheme
        get() = prefs.getString("theme", null)?.let(AppTheme::valueOf) ?: AppTheme.SYSTEM
        set(value) = prefs.edit { putString("theme", value.name) }

    var reminderStyle: ReminderStyle
        get() = prefs.getString("reminderStyle", null)?.let(ReminderStyle::valueOf) ?: ReminderStyle.NOTIFICATION
        set(value) = prefs.edit { putString("reminderStyle", value.name) }
}
