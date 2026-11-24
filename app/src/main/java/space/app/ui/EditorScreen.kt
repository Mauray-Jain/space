package space.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import space.app.data.Checklist
import space.app.data.Item
import space.app.data.ItemType
import space.app.data.Subtask
import space.app.data.tags
import space.app.data.withTag
import space.app.data.withoutTag

/** Edit anything after it's created (wishlist #2). Saves when you go back. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditorScreen(item: Item, onSave: (Item) -> Unit, onDelete: (Item) -> Unit, onBack: () -> Unit) {
    val original = remember(item.id) { Checklist.parse(item.text) }
    var body by rememberSaveable(item.id) { mutableStateOf(original.body) }
    var type by rememberSaveable(item.id) { mutableStateOf(item.type) }
    var pinned by rememberSaveable(item.id) { mutableStateOf(item.pinned) }
    var archived by rememberSaveable(item.id) { mutableStateOf(item.archived) }
    val subtasks = remember(item.id) { mutableStateListOf(*original.subtasks.toTypedArray()) }
    var newTag by rememberSaveable(item.id) { mutableStateOf("") }

    fun edited() = item.copy(
        text = Checklist(body, if (type == ItemType.TASK) subtasks.toList() else emptyList()).toText(),
        type = type,
        pinned = pinned,
        archived = archived,
    )

    fun back() {
        val e = edited()
        if (e != item && e.text.isNotBlank()) onSave(e)
        onBack()
    }
    BackHandler(onBack = ::back)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { TextButton(onClick = ::back) { Text("Back") } },
                actions = {
                    TextButton(onClick = { pinned = !pinned }) { Text(if (pinned) "Unpin" else "Pin") }
                    TextButton(onClick = { archived = !archived }) { Text(if (archived) "Unarchive" else "Archive") }
                    TextButton(onClick = { onDelete(item) }) { Text("Delete") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).imePadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Item text" },
                minLines = 3,
            )
            if (item.type != ItemType.SHOPPING) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = type == ItemType.NOTE, onClick = { type = ItemType.NOTE }, label = { Text("Note") })
                    FilterChip(selected = type == ItemType.TASK, onClick = { type = ItemType.TASK }, label = { Text("Task") })
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                tags(body).forEach { tag ->
                    InputChip(selected = false, onClick = { body = withoutTag(body, tag) }, label = { Text("#$tag  ×") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newTag,
                    onValueChange = { newTag = it.filterNot { c -> c.isWhitespace() || c == '#' } },
                    placeholder = { Text("Add tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { body = withTag(body, newTag); newTag = "" }, enabled = newTag.any { it.isLetter() }) { Text("Add") }
            }

            if (type == ItemType.TASK) {
                Text("Subtasks", style = MaterialTheme.typography.titleSmall)
                subtasks.forEachIndexed { i, subtask ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = subtask.done, onCheckedChange = { subtasks[i] = subtask.copy(done = it) })
                        OutlinedTextField(
                            value = subtask.text,
                            onValueChange = { subtasks[i] = subtask.copy(text = it.replace("\n", " ")) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { subtasks.removeAt(i) }) { Text("×") }
                    }
                }
                TextButton(onClick = { subtasks.add(Subtask("", false)) }) { Text("Add subtask") }
            }
        }
    }
}
