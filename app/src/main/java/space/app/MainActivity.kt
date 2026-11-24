package space.app

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
import space.app.ui.EditorScreen
import space.app.ui.HomeScreen
import space.app.ui.HomeViewModel
import space.app.ui.SpaceTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpaceTheme {
                // Two screens, so a plain variable instead of a navigation library.
                var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
                val state by viewModel.state.collectAsStateWithLifecycle()
                val editing = state.all.find { it.id == editingId }
                if (editing != null) {
                    EditorScreen(
                        item = editing,
                        onSave = viewModel::save,
                        onDelete = { viewModel.delete(listOf(it)); editingId = null },
                        onBack = { editingId = null },
                    )
                } else {
                    HomeScreen(viewModel, onOpen = { editingId = it.id })
                }
            }
        }
    }
}
