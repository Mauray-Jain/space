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

    var reminderStyle: ReminderStyle
        get() = prefs.getString("reminderStyle", null)?.let(ReminderStyle::valueOf) ?: ReminderStyle.NOTIFICATION
        set(value) = prefs.edit { putString("reminderStyle", value.name) }
}
