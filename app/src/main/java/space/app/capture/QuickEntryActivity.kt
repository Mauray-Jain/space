package space.app.capture

import android.content.Intent
import android.os.Bundle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

/** Opens just the capture sheet over whatever is on screen: from the Essential Key, the Quick Settings tile or the share sheet. */
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
        val problem = intent.getStringExtra(EXTRA_PROBLEM)
        if (savedInstanceState == null) {
            intent.getStringExtra(EXTRA_SCREENSHOT)?.let { viewModel.setShot(PendingShot(it, intent.getStringExtra(EXTRA_URL), intent.getStringExtra(EXTRA_APP))) }
        }
        setContent {
            SpaceTheme {
                val voice by viewModel.voice.state.collectAsStateWithLifecycle()
                val shot by viewModel.shot.collectAsStateWithLifecycle()
                QuickEntrySheet(
                    initialText = initialText,
                    canSaveEmpty = shot != null,
                    header = {
                        if (problem != null) Text(problem, color = MaterialTheme.colorScheme.error)
                        shot?.let { s ->
                            ScreenshotPreview(
                                shot = s,
                                onCrop = viewModel::crop,
                                onRemove = viewModel::removeShot,
                                onLonger = KeyService.instance?.let { service -> { service.captureLonger(s.path, s.url, s.app); finish() } },
                            )
                        }
                    },
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
                    onDismiss = { viewModel.discard(); finish() },
                )
            }
        }
    }

    companion object {
        /** Start listening right away (holding the Essential Key). */
        const val EXTRA_VOICE = "space.app.VOICE"
        /** A screen capture: the PNG waiting in the cache, the page's link and the app it came from. */
        const val EXTRA_SCREENSHOT = "space.app.SCREENSHOT"
        const val EXTRA_URL = "space.app.URL"
        const val EXTRA_APP = "space.app.APP"
        /** Why there's no screenshot (a secure screen). */
        const val EXTRA_PROBLEM = "space.app.PROBLEM"
    }
}
