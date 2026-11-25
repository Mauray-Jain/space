package space.app.capture

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import space.app.data.ItemRepository
import space.app.ui.Capture
import space.app.understand.Filer
import space.app.understand.Understood
import space.app.understand.understand
import space.app.remind.Reminders
import space.app.ui.formatWhen
import java.time.LocalDateTime

@HiltViewModel
class QuickEntryViewModel @Inject constructor(
    private val repo: ItemRepository,
    private val filer: Filer,
    private val reminders: Reminders,
) : ViewModel() {
    fun understand(text: String): Understood? = understand(text, LocalDateTime.now()).firstOrNull()

    // The activity finishes right after Save, so the write must outlive viewModelScope.
    fun save(c: Capture) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            // Saved from outside the app: show what a reminder became, with Undo and Edit.
            filer.file(c.type, c.text, c.remindAt, c.understood).forEach { id ->
                val item = repo.get(id) ?: return@forEach
                item.remindAt?.let { reminders.showCreated(item, "Reminder set · ${formatWhen(it)}") }
            }
        }
    }
}
