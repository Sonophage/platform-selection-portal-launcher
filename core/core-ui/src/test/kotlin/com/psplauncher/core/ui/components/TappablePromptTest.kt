package com.psplauncher.core.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Which prompts a tap may fire, and that tapping one dispatches the action the pad would.
 *
 * The policy half is the part worth pinning. The obvious implementation of "make the bar tappable"
 * is `actions.first()`, and it is wrong twice over: a fixed-icon prompt names a physical position
 * with no action behind it, and a multi-action prompt is one label over a range, so picking the
 * first would seek backwards when the user meant forwards. Both of those still have to RENDER --
 * they are legends -- which is why the policy cannot be "drop what you cannot tap".
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h480dp")
class TappablePromptTest {

    @get:Rule
    val compose = createComposeRule()

    // ── The policy, with no Compose involved ─────────────────────────────────

    @Test
    fun `a single remappable action is tappable`() {
        assertEquals(
            GamepadAction.OPEN_CONTEXT_MENU,
            ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options").tappableAction(),
        )
    }

    @Test
    fun `a range of actions under one label is not tappable`() {
        // "Prev / Next" on the shoulder buttons: a tap cannot say which way.
        assertNull(
            ControllerPromptItem(
                listOf(GamepadAction.PREV_CATEGORY, GamepadAction.NEXT_CATEGORY),
                "Prev / Next",
            ).tappableAction()
        )
    }

    @Test
    fun `a fixed position with no action behind it is not tappable`() {
        assertNull(ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Navigate").tappableAction())
    }

    @Test
    fun `drawn glyphs win over an action, so a hand-built item carrying both is not tappable`() {
        // `fixed()` leaves actions empty, so the fixedIcons half of the guard would look dead
        // without this: the case that makes it load bearing is an item built by hand with BOTH,
        // where the glyphs on screen are the fixed ones and the action is not what they name.
        // Tapping would then fire something the prompt is not showing.
        assertNull(
            ControllerPromptItem(
                actions = listOf(GamepadAction.SELECT),
                label = "Navigate",
                fixedIcons = listOf(ControllerIcon.DPAD_ALL),
            ).tappableAction()
        )
    }

    // ── The wiring ───────────────────────────────────────────────────────────

    @Test
    fun `tapping a prompt dispatches its action`() {
        var fired: GamepadAction? = null
        compose.setContent {
            ControllerPromptBar(
                items = listOf(ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options")),
                onAction = { fired = it },
            )
        }

        compose.onNodeWithText("Options").performClick()

        assertEquals(GamepadAction.OPEN_CONTEXT_MENU, fired)
    }

    @Test
    fun `a bar with no dispatcher stays a read-only legend`() {
        // The 29 existing footers pass nothing, so none of them may become tappable by surprise.
        compose.setContent {
            ControllerPromptBar(
                items = listOf(ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options")),
            )
        }

        // Renders, and clicking it cannot reach a dispatcher that does not exist.
        compose.onNodeWithText("Options").performClick()
    }

    @Test
    fun `an untappable prompt still renders and fires nothing when tapped`() {
        var fired: GamepadAction? = null
        compose.setContent {
            ControllerPromptBar(
                items = listOf(
                    ControllerPromptItem.fixed(ControllerIcon.DPAD_ALL, "Navigate"),
                    ControllerPromptItem(
                        listOf(GamepadAction.PREV_CATEGORY, GamepadAction.NEXT_CATEGORY),
                        "Prev / Next",
                    ),
                ),
                onAction = { fired = it },
            )
        }

        compose.onNodeWithText("Navigate").performClick()
        compose.onNodeWithText("Prev / Next").performClick()

        assertNull(fired)
    }
}
