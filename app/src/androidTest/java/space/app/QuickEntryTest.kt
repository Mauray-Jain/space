package space.app

import android.Manifest
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
import androidx.test.rule.GrantPermissionRule
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import space.app.capture.QuickEntryActivity

@RunWith(AndroidJUnit4::class)
class QuickEntryTest {
    // MainActivity asks for notifications at launch; the dialog would cover the app.
    @get:Rule(order = 0)
    val notificationsAllowed: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun savedEntryShowsInTheListAndCanBeDeleted() {
        val text = "buy stamps ${UUID.randomUUID()}"
        compose.onNodeWithContentDescription("New").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithText("Task").performClick()
        compose.onNodeWithText("Save").performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount(text) == 1 }
        compose.onNodeWithText(text).performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount(text) == 0 }

        // EDGE_CASES.md §9: Undo brings it back. The snackbar shows just after the row is gone, so wait for it.
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount("Undo") == 1 }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount(text) == 1 }
    }

    @Test
    fun subtasksAddedInTheEditorShowAsProgress() {
        val text = "pack for trip ${UUID.randomUUID()}"
        compose.onNodeWithContentDescription("New").performClick()
        compose.onNode(hasSetTextAction()).performTextInput(text)
        compose.onNodeWithText("Task").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount(text) == 1 }

        compose.onNodeWithText(text).performClick()
        compose.onNodeWithText("Add subtask").performClick()
        compose.onAllNodes(hasSetTextAction())[2].performTextInput("passport")
        compose.onNodeWithText("Back").performClick()

        compose.waitUntil(5_000) { compose.onAllNodesWithTextCount("0/1") >= 1 }
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
