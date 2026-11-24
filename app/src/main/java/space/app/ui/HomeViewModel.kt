package space.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.app.data.Item
import space.app.data.ItemRepository
import space.app.data.ItemType

@HiltViewModel
class HomeViewModel @Inject constructor(private val repo: ItemRepository) : ViewModel() {
    val items: StateFlow<List<Item>> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(type: ItemType, text: String) {
        viewModelScope.launch { repo.add(type, text) }
    }

    fun delete(item: Item) {
        viewModelScope.launch { repo.delete(item) }
    }
}
