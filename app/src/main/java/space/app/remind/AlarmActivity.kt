package space.app.remind

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import space.app.data.ItemRepository
import space.app.ui.SpaceTheme

@HiltViewModel
class AlarmViewModel @Inject constructor(private val repo: ItemRepository, private val actions: ReminderActions) : ViewModel() {
    suspend fun text(id: Long) = repo.get(id)?.text.orEmpty()
    fun done(id: Long, then: () -> Unit) { viewModelScope.launch { actions.done(id); then() } }
    fun snooze(id: Long, snooze: Snooze, then: () -> Unit) { viewModelScope.launch { actions.snooze(id, snooze); then() } }
}

/** Full-screen ringing reminder for the Alarm style. Shows over the lock screen. */
@AndroidEntryPoint
class AlarmActivity : ComponentActivity() {
    private val viewModel: AlarmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val id = intent.getLongExtra(Reminders.EXTRA_ID, -1)
        setContent {
            SpaceTheme {
                val text by produceState("", id) { value = viewModel.text(id) }
                Surface(Modifier.fillMaxSize()) {
                    Column(
                        Modifier.padding(32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(text, style = MaterialTheme.typography.headlineMedium)
                        Button(onClick = { viewModel.done(id, ::finish) }, Modifier.fillMaxWidth()) { Text("Done") }
                        Snooze.entries.forEach { s ->
                            OutlinedButton(onClick = { viewModel.snooze(id, s, ::finish) }, Modifier.fillMaxWidth()) { Text("Snooze ${s.label}") }
                        }
                    }
                }
            }
        }
    }
}
