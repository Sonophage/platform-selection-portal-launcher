package com.psplauncher.core.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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

/**
 * The name-prompt overlay's interaction contract, on a real composition.
 *
 * Every assertion here stands for something that was measured broken on the tablet when these
 * prompts were AlertDialogs, so none of them is decoration:
 *
 *  - the field opens focused, because the old prompt required a tap to type and a handheld has
 *    no pointer;
 *  - the keyboard's Done key commits, because on a soft keyboard it is the only commit affordance
 *    a thumb reaches, and the old dialog's Save button was the only one;
 *  - the scrim cancels and the card does not, because a mis-tap that destroyed a half-typed name
 *    would be worse than the trap it replaced.
 *
 * What these cannot check is the reason the overlay exists at all: that it draws in the launcher's
 * own window so Activity.dispatchKeyEvent still runs. Robolectric does not model the platform's
 * window stack faithfully enough for that to mean anything, so it is verified on the device
 * instead, by pressing B on the New Collection prompt.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h640dp")
class PfpTextPromptOverlayTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var confirmed: String? = null
    private var cancels = 0

    /** Renders the prompt with live text state, exactly as XMBShell's wrappers drive it. */
    private fun render(initial: String = "Shooters") {
        composeRule.setContent {
            var text by mutableStateOf(initial)
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
        // Control first: rendering alone must not fire anything, or the taps below prove nothing.
        assertTrue("rendering must not act on its own", cancels == 0 && confirmed == null)

        // Inside the card. A mis-tap here must not throw away a typed name.
        //
        // The explicit near-the-top offset is load-bearing. The card merges its children into one
        // semantics node, so a plain performClick() lands on the node's centre, which is the text
        // field -- the field consumes it and the card never sees it. That version of this
        // assertion stayed green with the card's click swallow deleted, so it proved nothing.
        composeRule.onNodeWithText("New Collection")
            .performTouchInput { click(Offset(24f, 12f)) }
        composeRule.waitForIdle()
        assertEquals("a tap inside the card must not cancel", 0, cancels)

        // Top-left corner: the card is centred and 320dp wide at minimum, so this is scrim.
        //
        // Selected by descendant rather than onRoot(): a focused text field raises its own cursor
        // handle in a second window, so there are two roots and onRoot() refuses to choose.
        composeRule.onAllNodes(isRoot())
            .filterToOne(hasAnyDescendant(hasText("New Collection")))
            .performTouchInput { click(Offset(4f, 4f)) }
        composeRule.waitForIdle()
        assertEquals("a tap on the scrim must cancel", 1, cancels)
        assertFalse("the scrim must never confirm", confirmed != null)
    }
}
