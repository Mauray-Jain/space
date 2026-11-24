package space.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import space.app.data.Item
import space.app.data.sectionOf

/** The one shopping list, grouped by aisle; bought items at the bottom, struck through. */
@Composable
fun ShoppingList(items: List<Item>, onAdd: (String) -> Unit, onToggle: (Item) -> Unit, onClearBought: () -> Unit) {
    var newItem by rememberSaveable { mutableStateOf("") }
    val (bought, toBuy) = items.partition { it.done }
    val sections = toBuy.groupBy { sectionOf(it.text) }.toSortedMap()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    placeholder = { Text("Add items, separated by commas") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onAdd(newItem); newItem = "" }, enabled = newItem.isNotBlank()) { Text("Add") }
            }
        }
        sections.forEach { (section, sectionItems) ->
            item(key = section.name) { SectionHeader(section.label) }
            items(sectionItems, key = { it.id }) { ShoppingRow(it, onToggle) }
        }
        if (bought.isNotEmpty()) {
            item(key = "bought") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("Bought", Modifier.weight(1f))
                    TextButton(onClick = onClearBought) { Text("Clear bought") }
                }
            }
            items(bought, key = { it.id }) { ShoppingRow(it, onToggle) }
        }
    }
}

@Composable
private fun SectionHeader(label: String, modifier: Modifier = Modifier) {
    Text(label, modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ShoppingRow(item: Item, onToggle: (Item) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = item.done, onCheckedChange = { onToggle(item) })
        Text(item.text, textDecoration = if (item.done) TextDecoration.LineThrough else null)
    }
}
