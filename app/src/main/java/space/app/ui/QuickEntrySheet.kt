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
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import space.app.capture.VoiceState
import space.app.data.ItemType
import space.app.understand.Kind
import space.app.understand.Understood
import space.app.understand.preview
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment

/** What the sheet hands back. [understood] is set when the automatic reading was kept (no chip or time changed). */
data class Capture(val type: ItemType, val text: String, val remindAt: LocalDateTime?, val understood: Understood?)

/** The type chip an automatic reading pre-selects. */
private fun chipFor(u: Understood): ItemType = when (u.type) {
    Kind.REMINDER, Kind.EVENT -> ItemType.REMINDER
    Kind.TASK -> ItemType.TASK
    Kind.LIST -> ItemType.SHOPPING
    else -> ItemType.NOTE
}

/**
 * The one capture sheet, used by the app, the Quick Settings tile and the share sheet.
 * The rules read the text as you type and pre-select a chip; tapping a chip or the time overrides them.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickEntrySheet(
    initialText: String,
    understand: (String) -> Understood?,
    onSave: (Capture) -> Unit,
    onDismiss: () -> Unit,
    voice: VoiceState,
    onMic: () -> Unit,
    initialType: ItemType = ItemType.NOTE,
    listenAtStart: Boolean = false,
    /** Shown above the text, e.g. a screenshot. */
    header: (@Composable () -> Unit)? = null,
    /** Save is allowed with no text (a screenshot on its own). */
    canSaveEmpty: Boolean = false,
) {
    var text by rememberSaveable { mutableStateOf(initialText) }
    var manualType by rememberSaveable { mutableStateOf<ItemType?>(initialType.takeIf { it != ItemType.NOTE }) }
    var manualTime by rememberSaveable { mutableStateOf<LocalDateTime?>(null) }
    val understood = remember(text) { if (manualType == null && manualTime == null) understand(text) else null }
    val type = manualType ?: understood?.let(::chipFor) ?: ItemType.NOTE
    val remindAt = manualTime ?: understood?.at ?: remember { defaultReminderTime() }
    val auto = manualType == null && manualTime == null
    val focus = remember { FocusRequester() }
    val context = LocalContext.current
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) onMic() }
    fun mic() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) onMic()
        else askMic.launch(Manifest.permission.RECORD_AUDIO)
    }
    // What you said goes into the text, where the rules read it like anything typed.
    LaunchedEffect(voice) {
        if (voice is VoiceState.Heard) text = listOf(text.trim(), voice.text).filter { it.isNotEmpty() }.joinToString(" ")
    }
    LaunchedEffect(Unit) { if (listenAtStart) mic() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            header?.invoke()
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Write anything") },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                minLines = 3,
            )
            val u = understood
            if (auto && u != null && text.isNotBlank()) {
                Text(preview(u, ::formatWhen), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ItemType.NOTE to "Note", ItemType.TASK to "Task", ItemType.REMINDER to "Reminder", ItemType.SHOPPING to "Shopping").forEach { (t, label) ->
                    FilterChip(selected = type == t, onClick = { manualType = t }, label = { Text(label) })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = ::mic, enabled = voice != VoiceState.Transcribing) {
                    Text(
                        when (voice) {
                            VoiceState.Listening -> "Stop"
                            VoiceState.Transcribing -> "Writing…"
                            else -> "Speak"
                        },
                    )
                }
                if (voice is VoiceState.Problem) Text(voice.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (type == ItemType.REMINDER) WhenPicker(remindAt) { manualTime = it; manualType = ItemType.REMINDER }
                Spacer(Modifier.weight(1f))
                val asks = auto && u?.type == Kind.QUESTION
                Button(
                    onClick = { onSave(Capture(type, text, remindAt.takeIf { type == ItemType.REMINDER }, u.takeIf { auto })) },
                    enabled = text.isNotBlank() || canSaveEmpty,
                ) { Text(if (asks) "Show" else "Save") }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
