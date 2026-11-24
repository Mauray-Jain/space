package space.app.capture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import space.app.ui.QuickEntrySheet
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
        setContent {
            SpaceTheme {
                QuickEntrySheet(
                    initialText = initialText,
                    onSave = { type, text, at -> viewModel.save(type, text, at); finish() },
                    onDismiss = { finish() },
                )
            }
        }
    }
}
