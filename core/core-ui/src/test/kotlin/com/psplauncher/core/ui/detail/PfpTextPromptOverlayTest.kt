package com.psplauncher.core.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import com.psplauncher.core.ui.preview.PfpScreenPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h640dp")
class PfpTextPromptOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var confirmed: String? = null
    private var cancels = 0

    private fun render(initial: String = "Shooters") {
        composeRule.setContent {
            var text by remember { mutableStateOf(initial) }
            PfpScreenPreview {
                PfpTextPromptOverlay(
                    title = "New Collection",
                    value = text,
                    placeholder = "e.g. RPGs, Currently Playing",
                    onValueChange = { text = it },
                    onConfirm = { confirmed = text },
                    onCancel = { cancels++ },
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the field opens focused, so the keyboard is up without a tap`() {
        render()
        composeRule.onNodeWithText("Shooters").assertIsFocused()
    }

    @Test
    fun `the keyboard's Done key commits the name`() {
        render(initial = "")
        composeRule.onNodeWithText("e.g. RPGs, Currently Playing").performTextInput("Backlog")
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Backlog").performImeAction()
        composeRule.waitForIdle()

        assertEquals("Done must confirm with the typed name", "Backlog", confirmed)
        assertEquals("Done must not also cancel", 0, cancels)
    }

    @Test
    fun `the Save button commits and Cancel does not`() {
        render()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        assertEquals("Cancel must cancel exactly once", 1, cancels)
        assertEquals("Cancel must never confirm", null, confirmed)

        composeRule.onNodeWithText("Save").performClick()
        composeRule.waitForIdle()
        assertEquals("Save must confirm the current text", "Shooters", confirmed)
    }

    @Test
    fun `tapping the scrim cancels but tapping the card does not`() {
        render()

        assertTrue("rendering must not act on its own", cancels == 0 && confirmed == null)

        composeRule.onNodeWithText("New Collection")
            .performTouchInput { click(Offset(24f, 12f)) }
        composeRule.waitForIdle()
        assertEquals("a tap inside the card must not cancel", 0, cancels)

        composeRule.onAllNodes(isRoot())
            .filterToOne(hasAnyDescendant(hasText("New Collection")))
            .performTouchInput { click(Offset(4f, 4f)) }
        composeRule.waitForIdle()
        assertEquals("a tap on the scrim must cancel", 1, cancels)
        assertFalse("the scrim must never confirm", confirmed != null)
    }
}
