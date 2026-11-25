package space.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import space.app.remind.Reminders
import space.app.ui.EditorScreen
import space.app.ui.HomeScreen
import space.app.ui.HomeViewModel
import space.app.ui.SettingsScreen
import space.app.ui.SettingsViewModel
import space.app.ui.SpaceTheme
import space.app.understand.Kind
import space.app.understand.Understood

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()

    /** An item to open, from a notification's Edit button or tap. */
    private val openRequest = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
        setContent {
            SpaceTheme {
                // Three screens, so plain state instead of a navigation library.
                var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
                var inSettings by rememberSaveable { mutableStateOf(false) }
                val request by openRequest.collectAsStateWithLifecycle()
                request?.let { editingId = it; inSettings = false; openRequest.value = null }

                val state by viewModel.state.collectAsStateWithLifecycle()
                val editing = state.all.find { it.id == editingId }
                when {
                    inSettings -> SettingsScreen(settingsViewModel, onBack = { inSettings = false })
                    editing != null -> EditorScreen(
                        item = editing,
                        recording = viewModel.recordingOf(editing),
                        contactForm = viewModel.contactFormFor(editing),
                        onSave = viewModel::save,
                        onDelete = { viewModel.delete(listOf(it)); editingId = null },
                        onBack = { editingId = null },
                    )
                    else -> HomeScreen(viewModel, onOpen = { editingId = it.id }, onSettings = { inSettings = true })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        val id = intent.getLongExtra(Reminders.EXTRA_ID, -1)
        if (id >= 0) openRequest.value = id
        val target = intent.getStringExtra(EXTRA_QUESTION_TARGET)
        val search = intent.getStringExtra(EXTRA_QUESTION_SEARCH)
        if (target != null || search != null) viewModel.ask(Understood(Kind.QUESTION, target = target, search = search))
    }

    companion object {
        const val EXTRA_QUESTION_TARGET = "space.app.QUESTION_TARGET"
        const val EXTRA_QUESTION_SEARCH = "space.app.QUESTION_SEARCH"
    }
}
