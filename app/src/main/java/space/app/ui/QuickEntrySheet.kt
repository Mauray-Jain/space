package space.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import space.app.data.ItemType

/** The one capture sheet, used by the app, the Quick Settings tile and the share sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntrySheet(
    initialText: String,
    onSave: (ItemType, String, LocalDateTime?) -> Unit,
    onDismiss: () -> Unit,
    initialType: ItemType = ItemType.NOTE,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var type by rememberSaveable { mutableStateOf(initialType) }
    var remindAt by rememberSaveable { mutableStateOf(defaultReminderTime()) }
    val focus = remember { FocusRequester() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Write anything") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                minLines = 3,
            )
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == ItemType.NOTE, onClick = { type = ItemType.NOTE }, label = { Text("Note") })
                FilterChip(selected = type == ItemType.TASK, onClick = { type = ItemType.TASK }, label = { Text("Task") })
                FilterChip(selected = type == ItemType.REMINDER, onClick = { type = ItemType.REMINDER }, label = { Text("Reminder") })
                FilterChip(selected = type == ItemType.SHOPPING, onClick = { type = ItemType.SHOPPING }, label = { Text("Shopping") })
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (type == ItemType.REMINDER) WhenPicker(remindAt) { remindAt = it }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSave(type, text, remindAt.takeIf { type == ItemType.REMINDER }) }, enabled = text.isNotBlank()) { Text("Save") }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
