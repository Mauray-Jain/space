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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
class SettingsViewModel @Inject constructor(private val settings: Settings, private val repo: ItemRepository) : ViewModel() {
    val reminderStyle get() = settings.reminderStyle

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
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
