package space.app.ui

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import space.app.ReminderStyle
import space.app.Settings
import space.app.data.ItemRepository

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: Settings,
    private val repo: ItemRepository,
    private val calendar: space.app.sync.CalendarSync,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : ViewModel() {
    val markdownFolder get() = settings.markdownFolder
    val calendarId get() = settings.calendarId
    fun calendars() = calendar.calendars()

    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    /** The result of the last export or import. */
    val message: kotlinx.coroutines.flow.StateFlow<String?> = _message

    fun setMarkdownFolder(uri: android.net.Uri?) {
        uri?.let { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        settings.markdownFolder = uri?.toString()
        if (uri != null) viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repo.resyncAll() }
    }

    fun setCalendar(id: Long) {
        settings.calendarId = id
        if (id >= 0) viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) { repo.resyncAll() }
    }

    fun export(uri: android.net.Uri, password: String) = viewModelScope.launch {
        _message.value = runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { space.app.sync.Backup.export(repo, it, password.ifEmpty { null }) }
            "Exported. This file is outside Space: deleting items later won't remove them from it."
        }.getOrElse { "Export failed: ${it.message}" }
    }

    fun import(uri: android.net.Uri, password: String) = viewModelScope.launch {
        _message.value = runCatching {
            val n = context.contentResolver.openInputStream(uri)!!.use { space.app.sync.Backup.import(repo, it, password.ifEmpty { null }) }
            "Imported $n items."
        }.getOrElse { "Import failed (wrong password or not a Space export)." }
    }

    val reminderStyle get() = settings.reminderStyle
    val keyCode get() = settings.keyCode
    val learningKey get() = settings.learningKey
    var keyEnabled
        get() = settings.keyEnabled
        set(value) { settings.keyEnabled = value }

    fun learnKey(on: Boolean) { settings.learningKey = on }

    val theme get() = settings.theme
    fun setTheme(value: space.app.AppTheme) { settings.theme = value }

    var morningSummary
        get() = settings.morningSummary
        set(value) {
            settings.morningSummary = value
            space.app.remind.MorningAlarm.schedule(context, value)
        }

    fun setReminderStyle(style: ReminderStyle) {
        settings.reminderStyle = style
        viewModelScope.launch { repo.rearmAll() } // the style decides how alarms are set
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(viewModel.reminderStyle) }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Reminder style", style = MaterialTheme.typography.titleSmall)
            ReminderStyle.entries.forEach { s ->
                Row(
                    Modifier.selectable(selected = style == s, onClick = { style = s; viewModel.setReminderStyle(s) }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = style == s, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(s.label)
                        Text(s.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (style == ReminderStyle.ALARM && Build.VERSION.SDK_INT >= 34 &&
                !context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
            ) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
                }) { Text("Allow full-screen alarms") }
            }

            Appearance(viewModel)
            EssentialKey(viewModel)
            MarkdownFolder(viewModel)
            CalendarChoice(viewModel)
            ExportImport(viewModel)

            Text("Reliability", style = MaterialTheme.typography.titleSmall)
            Text(
                "Some phones stop apps in the background. Allow unrestricted battery use so reminders always ring.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!isUnrestricted(context)) {
                OutlinedButton(onClick = { requestUnrestricted(context) }) { Text("Allow unrestricted battery") }
            } else {
                Text("Battery use is unrestricted.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun isUnrestricted(context: Context) =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

@SuppressLint("BatteryLife") // Reminders must ring on time; see EDGE_CASES.md §8.
private fun requestUnrestricted(context: Context) {
    context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
}

/** Turning on the key service and teaching it which key is the Essential Key (its code isn't documented). */
@Composable
private fun EssentialKey(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    var learning by remember { mutableStateOf(viewModel.learningKey) }
    var keyCode by remember { mutableStateOf(viewModel.keyCode) }
    var enabled by remember { mutableStateOf(viewModel.keyEnabled) }
    val serviceOn = space.app.capture.KeyService.instance != null
    // Waits for the key press the service records.
    androidx.compose.runtime.LaunchedEffect(learning) {
        while (learning) {
            kotlinx.coroutines.delay(300)
            if (!viewModel.learningKey) { learning = false; keyCode = viewModel.keyCode }
        }
    }
    Text("Essential Key", style = MaterialTheme.typography.titleSmall)
    Text("Press to save the screen, hold to speak, double-press to open Space.", style = MaterialTheme.typography.bodySmall)
    if (!serviceOn) {
        OutlinedButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Turn on in Accessibility settings") }
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Use the key", Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it; viewModel.keyEnabled = it })
    }
    when {
        learning -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Press the Essential Key now…", Modifier.weight(1f))
            TextButton(onClick = { viewModel.learnKey(false); learning = false }) { Text("Cancel") }
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (keyCode < 0) "Key not set up yet" else "Key set (code $keyCode)", Modifier.weight(1f))
            OutlinedButton(onClick = { viewModel.learnKey(true); learning = true }) { Text(if (keyCode < 0) "Set up" else "Set up again") }
        }
    }
}

@Composable
private fun MarkdownFolder(viewModel: SettingsViewModel) {
    var folder by remember { mutableStateOf(viewModel.markdownFolder) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) { viewModel.setMarkdownFolder(uri); folder = uri.toString() }
    }
    Text("Markdown folder", style = MaterialTheme.typography.titleSmall)
    Text(
        "A one-way copy of every item as a .md file (Obsidian, Syncthing). Edits there aren't read back, and copies already synced to other devices aren't deleted with the item.",
        style = MaterialTheme.typography.bodySmall,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(folder?.let { Uri.decode(it).substringAfterLast(':') }?.ifEmpty { "Chosen" } ?: "Off", Modifier.weight(1f))
        OutlinedButton(onClick = { pick.launch(null) }) { Text("Choose") }
        if (folder != null) TextButton(onClick = { viewModel.setMarkdownFolder(null); folder = null }) { Text("Turn off") }
    }
}

@Composable
private fun CalendarChoice(viewModel: SettingsViewModel) {
    var chosen by remember { mutableStateOf(viewModel.calendarId) }
    var calendars by remember { mutableStateOf(viewModel.calendars()) }
    var menu by remember { mutableStateOf(false) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) {
        calendars = viewModel.calendars(); menu = calendars.isNotEmpty()
    }
    Text("Calendar", style = MaterialTheme.typography.titleSmall)
    Text("Events (\"dentist Thursday 3pm\") also go to a calendar on this phone. Deleting the item deletes the event.", style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(calendars.find { it.id == chosen }?.let { "${it.name} (${it.account})" } ?: "Off", Modifier.weight(1f))
        Box {
            OutlinedButton(onClick = {
                if (calendars.isEmpty()) ask.launch(arrayOf(android.Manifest.permission.READ_CALENDAR, android.Manifest.permission.WRITE_CALENDAR)) else menu = true
            }) { Text("Choose") }
            androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("Off") }, onClick = { viewModel.setCalendar(-1); chosen = -1; menu = false })
                calendars.forEach { c ->
                    androidx.compose.material3.DropdownMenuItem(text = { Text("${c.name} (${c.account})") }, onClick = { viewModel.setCalendar(c.id); chosen = c.id; menu = false })
                }
            }
        }
    }
}

@Composable
private fun ExportImport(viewModel: SettingsViewModel) {
    var password by remember { mutableStateOf("") }
    val message by viewModel.message.collectAsState()
    val export = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.export(uri, password)
    }
    val import = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.import(uri, password)
    }
    Text("Export and import", style = MaterialTheme.typography.titleSmall)
    Text("Everything in one .zip, saved wherever you choose. Nothing is uploaded.", style = MaterialTheme.typography.bodySmall)
    androidx.compose.material3.OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password (optional)") },
        singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { export.launch("space-export.zip") }) { Text("Export") }
        OutlinedButton(onClick = { import.launch(arrayOf("application/zip", "application/octet-stream")) }) { Text("Import") }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun Appearance(viewModel: SettingsViewModel) {
    val activity = LocalContext.current as? android.app.Activity
    var summary by remember { mutableStateOf(viewModel.morningSummary) }
    Text("Theme", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        space.app.AppTheme.entries.forEach { t ->
            androidx.compose.material3.FilterChip(
                selected = viewModel.theme == t,
                onClick = { viewModel.setTheme(t); activity?.recreate() },
                label = { Text(t.label) },
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Morning summary")
            Text("8 AM: your open tasks with no time.", style = MaterialTheme.typography.bodySmall)
        }
        androidx.compose.material3.Switch(checked = summary, onCheckedChange = { summary = it; viewModel.morningSummary = it })
    }
}
