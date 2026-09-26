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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h480dp")
class TappablePromptTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a single remappable action is tappable`() {
        assertEquals(
            GamepadAction.OPEN_CONTEXT_MENU,
            ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options").tappableAction(),
        )
    }

    @Test
    fun `a range of actions under one label is not tappable`() {
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
        assertNull(
            ControllerPromptItem(
                actions = listOf(GamepadAction.SELECT),
                label = "Navigate",
                fixedIcons = listOf(ControllerIcon.DPAD_ALL),
            ).tappableAction()
        )
    }

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
        compose.setContent {
            ControllerPromptBar(
                items = listOf(ControllerPromptItem(GamepadAction.OPEN_CONTEXT_MENU, "Options")),
            )
        }

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
