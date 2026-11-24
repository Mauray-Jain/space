package space.app.capture

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import space.app.data.ItemRepository
import space.app.data.ItemType
import space.app.remind.Reminders
import space.app.ui.formatWhen
import java.time.LocalDateTime

@HiltViewModel
class QuickEntryViewModel @Inject constructor(
    private val repo: ItemRepository,
    private val reminders: Reminders,
) : ViewModel() {
    // The activity finishes right after Save, so the write must outlive viewModelScope.
    fun save(type: ItemType, text: String, remindAt: LocalDateTime?) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val id = repo.add(type, text, remindAt)
            // Saved from outside the app: show what happened, with Undo and Edit.
            if (remindAt != null) repo.get(id)?.let { reminders.showCreated(it, "Reminder set · ${formatWhen(remindAt)}") }
        }
    }
}
