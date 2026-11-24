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

@HiltViewModel
class QuickEntryViewModel @Inject constructor(private val repo: ItemRepository) : ViewModel() {
    // The activity finishes right after Save, so the write must outlive viewModelScope.
    fun save(type: ItemType, text: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { repo.add(type, text) }
    }
}
