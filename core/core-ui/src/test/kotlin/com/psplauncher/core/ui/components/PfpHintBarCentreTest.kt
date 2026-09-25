package com.psplauncher.core.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.preview.PfpScreenPreview
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The bar's centre slot, which is what let Settings stop being the last screen with a black pill.
 *
 * Settings' prompts used to sit at the right-hand end of a 44dp band reserved for the focused
 * row's explanation. Stacking the shared bar under that band would have cost 78dp of a 462dp
 * screen — the exact strip the band replaced — so the explanation moved INTO the bar, in the gap
 * between the two prompt groups that every other screen leaves empty.
 *
 * Two things have to hold for that to work, and neither is obvious from reading the layout:
 * the centre has to be drawn at all, and it has to survive a bar with no prompts in it. The
 * second is the one that bites: the bar returns early on an empty item list, and Settings hands
 * it an empty list whenever Display ▸ Button Hints is off — at which point the band would vanish,
 * the list below would grow by 34dp, and every row would move under the cursor.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h640dp")
class PfpHintBarCentreTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the centre is drawn between the prompts`() {
        composeRule.setContent {
            PfpScreenPreview {
                PfpHintBar(
                    items = listOf(
                        ControllerPromptItem(GamepadAction.BACK, "Back"),
                        ControllerPromptItem(GamepadAction.SELECT, "Enter"),
                    ),
                    centre = { Text("What this row does") },
                )
            }
        }
        composeRule.onNodeWithText("What this row does").assertIsDisplayed()

        // To the RIGHT of the left-hand group. Centre content that rendered before the prompts
        // would still "display", and would sit on top of them.
        val back = composeRule.onNodeWithText("Back").fetchSemanticsNode().positionInRoot.x
        val help = composeRule.onNodeWithText("What this row does").fetchSemanticsNode().positionInRoot.x
        assertTrue("the centre ($help) must sit right of Back ($back)", help > back)
    }

    /**
     * THE case this test exists for. Turning the hints off must not collapse the band.
     */
    @Test
    fun `a bar with no prompts still draws its centre`() {
        composeRule.setContent {
            PfpScreenPreview {
                PfpHintBar(items = emptyList(), centre = { Text("Still here") })
            }
        }
        composeRule.onNodeWithText("Still here").assertIsDisplayed()
    }

    @Test
    fun `a bar with neither prompts nor a centre draws nothing`() {
        composeRule.setContent {
            PfpScreenPreview { PfpHintBar(items = emptyList()) }
        }
        // Nothing to assert on but the absence; the point is that it must not crash or reserve a
        // band on a screen that asked for no bar at all.
        composeRule.onNodeWithText("Back").assertDoesNotExist()
    }
}
