package space.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import space.app.data.Deleted
import space.app.data.Item
import space.app.data.ItemRepository
import space.app.data.ItemType
import space.app.data.tags
import space.app.understand.Filer
import space.app.understand.Kind
import space.app.understand.Understood
import space.app.understand.understand

data class HomeState(
    val items: List<Item> = emptyList(),
    val all: List<Item> = emptyList(),
    val filter: Filter = Filter.ALL,
    val tag: String? = null,
    val sort: Sort = Sort.NEWEST,
    val allTags: List<String> = emptyList(),
)

@HiltViewModel
class HomeViewModel @Inject constructor(private val repo: ItemRepository, private val filer: Filer) : ViewModel() {
    private val filter = MutableStateFlow(Filter.ALL)
    private val tag = MutableStateFlow<String?>(null)
    private val sort = MutableStateFlow(Sort.NEWEST)

    val state: StateFlow<HomeState> =
        combine(repo.observeAll(), filter, tag, sort) { all, f, t, s ->
            HomeState(feed(all, f, t, s), all, f, t, s, all.filter { !it.archived }.flatMap { tags(it.text) }.distinct().sorted())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private val _query = MutableStateFlow<String?>(null)
    /** null = not searching. */
    val query: StateFlow<String?> = _query.asStateFlow()
    private val _searchResults = MutableStateFlow<List<Item>?>(null)
    val searchResults: StateFlow<List<Item>?> = _searchResults.asStateFlow()
    private var searchJob: Job? = null

    private val _undoable = MutableStateFlow<List<Deleted>>(emptyList())
    /** What the last delete removed. Held in memory for ~5 s only (EDGE_CASES.md §9). */
    val undoable: StateFlow<List<Deleted>> = _undoable.asStateFlow()
    private var forgetJob: Job? = null

    fun setFilter(value: Filter) { filter.value = value }
    fun setTag(value: String?) { tag.value = value }
    fun setSort(value: Sort) { sort.value = value }

    fun search(query: String) {
        _query.value = query
        searchJob?.cancel()
        if (query.isBlank()) { _searchResults.value = null; return }
        searchJob = viewModelScope.launch {
            delay(150)
            _searchResults.value = repo.search(query)
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _query.value = null
        _searchResults.value = null
    }

    fun add(type: ItemType, text: String, remindAt: LocalDateTime? = null) {
        viewModelScope.launch { repo.add(type, text, remindAt) }
    }

    fun understand(text: String): Understood? = understand(text, LocalDateTime.now()).firstOrNull()

    /** Files a capture, or answers it if it was a question ("ask about your stuff"). */
    fun capture(c: Capture) {
        val u = c.understood
        if (u?.type == Kind.QUESTION) return ask(u)
        viewModelScope.launch { filer.file(c.type, c.text, c.remindAt, u) }
    }

    /** No generated answer, just the right items: the shopping list, or a search (PLAN.md §2). */
    fun ask(u: Understood) {
        if (u.target == "shopping_list") { closeSearch(); setFilter(Filter.SHOPPING) } else search(u.search.orEmpty())
    }

    fun save(item: Item) {
        viewModelScope.launch { repo.update(item) }
    }

    fun toggleDone(item: Item) = save(item.copy(done = !item.done))

    fun delete(items: Collection<Item>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _undoable.value = items.map { repo.delete(it) }
            forgetJob?.cancel()
            forgetJob = launch { delay(5_000); _undoable.value = emptyList() }
            _searchResults.value?.let { results -> _searchResults.value = results - items.toSet() }
        }
    }

    fun undo() {
        val toRestore = _undoable.value
        _undoable.value = emptyList()
        forgetJob?.cancel()
        viewModelScope.launch { toRestore.forEach { repo.restore(it) } }
    }

    fun forgetUndo() {
        _undoable.value = emptyList()
    }
}
