package space.app

import android.content.Intent
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import space.app.capture.QuickEntryActivity

@RunWith(AndroidJUnit4::class)
class QuickEntryTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun savedEntryShowsInTheListAndCanBeDeleted() {
        val text = "buy stamps ${UUID.randomUUID()}"
        compose.onNodeWithContentDescription("New").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithText("Task").performClick()
        compose.onNodeWithText("Save").performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount(text) == 1 }
        compose.onNodeWithContentDescription("Delete $text").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount(text) == 0 }
    }
}

@RunWith(AndroidJUnit4::class)
class ShareTargetTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun sharedTextOpensPrefilledAsANote() {
        val intent = Intent(ApplicationProvider.getApplicationContext(), QuickEntryActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "https://example.com/article")
        ActivityScenario.launch<QuickEntryActivity>(intent).use {
            compose.onNodeWithText("https://example.com/article").assertExists()
            compose.onNodeWithText("Note").assertIsSelected()
        }
    }
}

private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTextCount(text: String) =
    onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().size
