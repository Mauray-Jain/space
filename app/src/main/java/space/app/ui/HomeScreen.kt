package space.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import space.app.data.Checklist
import space.app.data.Item
import space.app.data.ItemType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpen: (Item) -> Unit, onSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val undoable by viewModel.undoable.collectAsStateWithLifecycle()
    val voiceState by viewModel.voice.state.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searching = query != null
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(undoable) {
        if (undoable.isNotEmpty()) {
            val n = undoable.size
            val result = snackbar.showSnackbar(if (n == 1) "Deleted" else "Deleted $n items", actionLabel = "Undo")
            if (result == SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.forgetUndo()
        } else {
            snackbar.currentSnackbarData?.dismiss()
        }
    }

    fun open(item: Item) {
        if (selected.isNotEmpty()) selected = selected.toggle(item.id) else onOpen(item)
    }

    Scaffold(
        topBar = {
            when {
                selected.isNotEmpty() -> TopAppBar(
                    title = { Text("${selected.size} selected") },
                    actions = {
                        TextButton(onClick = {
                            viewModel.delete(state.all.filter { it.id in selected })
                            selected = emptySet()
                        }) { Text("Delete") }
                        TextButton(onClick = { selected = emptySet() }) { Text("Cancel") }
                    },
                )
                searching -> TopAppBar(
                    title = {
                        TextField(
                            value = query.orEmpty(),
                            onValueChange = viewModel::search,
                            placeholder = { Text("Search everything") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search text" },
                        )
                    },
                    actions = {
                        TextButton(onClick = viewModel::closeSearch) { Text("Close") }
                    },
                )
                else -> TopAppBar(
                    title = { Text("Space") },
                    actions = {
                        TextButton(onClick = { viewModel.search("") }) { Text("Search") }
                        SortMenu(state.sort, viewModel::setSort)
                        TextButton(onClick = onSettings) { Text("Settings") }
                    },
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { adding = true },
                modifier = Modifier.semantics { contentDescription = "New" },
            ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val results = searchResults
            if (searching && results != null) {
                ItemList(results, selected, ::open, { selected = selected.toggle(it.id) }, viewModel::toggleDone, empty = "No matches")
                return@Column
            }
            FilterRow(state, viewModel::setFilter, viewModel::setTag)
            if (state.filter == Filter.SHOPPING) {
                ShoppingList(
                    items = state.items,
                    onAdd = { viewModel.add(ItemType.SHOPPING, it) },
                    onToggle = viewModel::toggleDone,
                    onClearBought = { viewModel.delete(state.items.filter { it.done }) },
                )
            } else {
                val toBuy = state.all.count { it.type == ItemType.SHOPPING && !it.done && !it.archived }
                ItemList(
                    items = state.items,
                    selected = selected,
                    onClick = ::open,
                    onLongClick = { selected = selected.toggle(it.id) },
                    onToggleDone = viewModel::toggleDone,
                    empty = "Nothing here yet",
                    header = if (state.filter == Filter.ALL && state.tag == null && toBuy > 0) {
                        { ShoppingSummary(toBuy) { viewModel.setFilter(Filter.SHOPPING) } }
                    } else null,
                )
            }
        }
    }

    if (adding) {
        QuickEntrySheet(
            initialText = "",
            initialType = if (state.filter == Filter.SHOPPING) ItemType.SHOPPING else ItemType.NOTE,
            understand = viewModel::understand,
            onSave = { viewModel.capture(it); adding = false },
            onDismiss = { viewModel.voice.reset(); adding = false },
            voice = voiceState,
            onMic = viewModel.voice::toggle,
        )
    }
}

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

@Composable
private fun SortMenu(sort: Sort, onSort: (Sort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("Sort") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Sort.entries.forEach {
                DropdownMenuItem(
                    text = { Text(if (it == sort) "✓ ${it.label}" else it.label) },
                    onClick = { onSort(it); open = false },
                )
            }
        }
    }
}

@Composable
private fun FilterRow(state: HomeState, onFilter: (Filter) -> Unit, onTag: (String?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Filter.entries) { f ->
            FilterChip(selected = state.filter == f, onClick = { onFilter(f) }, label = { Text(f.label) })
        }
        items(state.allTags) { t ->
            FilterChip(selected = state.tag == t, onClick = { onTag(if (state.tag == t) null else t) }, label = { Text("#$t") })
        }
    }
}

@Composable
private fun ShoppingSummary(toBuy: Int, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Text("Shopping list · $toBuy to buy", Modifier.padding(16.dp))
    }
}

@Composable
fun ItemList(
    items: List<Item>,
    selected: Set<Long>,
    onClick: (Item) -> Unit,
    onLongClick: (Item) -> Unit,
    onToggleDone: (Item) -> Unit,
    empty: String,
    header: (@Composable () -> Unit)? = null,
) {
    if (items.isEmpty() && header == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(empty, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (header != null) item { header() }
        items(items, key = { it.id }) { item ->
            ItemCard(item, item.id in selected, { onClick(item) }, { onLongClick(item) }, { onToggleDone(item) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCard(item: Item, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onToggleDone: () -> Unit) {
    val checklist = Checklist.parse(item.text)
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (item.type != ItemType.NOTE) {
                Checkbox(checked = item.done, onCheckedChange = { onToggleDone() })
            }
            Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                Text(
                    text = checklist.body.ifEmpty { item.text },
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (item.done) TextDecoration.LineThrough else null,
                    color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val meta = listOfNotNull(
                    "Pinned".takeIf { item.pinned },
                    item.remindAt?.let { at -> formatWhen(item.snoozedUntil ?: at) + (item.repeat?.let { " · " + space.app.remind.describe(it) } ?: "") },
                    checklist.subtasks.takeIf { it.isNotEmpty() }?.let { s -> "${s.count { it.done }}/${s.size}" },
                )
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
