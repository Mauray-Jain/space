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

    var reminderStyle: ReminderStyle
        get() = prefs.getString("reminderStyle", null)?.let(ReminderStyle::valueOf) ?: ReminderStyle.NOTIFICATION
        set(value) = prefs.edit { putString("reminderStyle", value.name) }
}
