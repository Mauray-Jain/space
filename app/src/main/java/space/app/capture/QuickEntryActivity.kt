package space.app.capture

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import space.app.MainActivity
import space.app.ui.QuickEntrySheet
import space.app.understand.Kind
import space.app.ui.SpaceTheme

/** Opens just the capture sheet over whatever is on screen: from the Quick Settings tile or the share sheet. */
@AndroidEntryPoint
class QuickEntryActivity : ComponentActivity() {
    private val viewModel: QuickEntryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialText = if (intent.action == Intent.ACTION_SEND) {
            sharedText(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
        } else {
            ""
        }
        val listen = intent.getBooleanExtra(EXTRA_VOICE, false)
        setContent {
            SpaceTheme {
                val voice by viewModel.voice.state.collectAsStateWithLifecycle()
                QuickEntrySheet(
                    initialText = initialText,
                    voice = voice,
                    onMic = viewModel.voice::toggle,
                    listenAtStart = listen,
                    understand = viewModel::understand,
                    onSave = { c ->
                        val u = c.understood
                        if (u?.type == Kind.QUESTION) {
                            // Questions are answered in the app.
                            startActivity(
                                Intent(this, MainActivity::class.java)
                                    .putExtra(MainActivity.EXTRA_QUESTION_TARGET, u.target)
                                    .putExtra(MainActivity.EXTRA_QUESTION_SEARCH, u.search)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } else {
                            viewModel.save(c)
                        }
                        finish()
                    },
                    onDismiss = { viewModel.voice.reset(); finish() },
                )
            }
        }
    }

    companion object {
        /** Start listening right away (voice capture from the Essential Key, milestone 6). */
        const val EXTRA_VOICE = "space.app.VOICE"
    }
}
