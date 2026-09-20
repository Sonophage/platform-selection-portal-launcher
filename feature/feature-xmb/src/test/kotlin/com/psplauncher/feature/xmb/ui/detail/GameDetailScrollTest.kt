package com.psplauncher.feature.xmb.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.ui.preview.PfpScreenPreview
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Focus-driven scrolling on a real composition.
 *
 * This guards a crash, not just a behaviour: the page's scroll effect used to branch on
 * `ScrollState.animateScrollTo`, whose Unit result gets materialized as `checkcast kotlin.Unit` in a
 * branch — while the call itself is compiled as a discarded Float-returning `animateScrollBy`. Every
 * time that scroll suspended, the resumed Float hit the cast and opening the page died with
 * `ClassCastException: Float cannot be cast to kotlin.Unit`. Rendering the page is therefore the
 * regression test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h640dp")
class GameDetailScrollTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val game = Game(
        id = 1L,
        title = "Crash Bandicoot",
        platformId = "psx",
        romPath = "/roms/psx/crash.bin",
        releaseYear = 1996,
        developer = "Naughty Dog",
        // Long enough to push the media strip below the fold, which is what gives this test
        // something to measure. The redesign dropped the 220dp hero card and the icon-tile row,
        // and with a one-line description the whole page then fit the viewport: the test passed
        // by having nothing to scroll.
        description = "Bandicoot jumps. ".repeat(10),
    )

    private fun state(focus: String?) = GameDetailUiState(
        isLoading = false,
        game = game,
        navFocusKey = focus,
        cursorVisible = true,
        detailMedia = listOf(
            DetailMedia("/tmp/clip.mp4", isVideo = true),
            DetailMedia("/tmp/shot.png", isVideo = false),
        ),
    )

    private fun render(flow: MutableStateFlow<GameDetailUiState>) {
        val viewModel = mockk<GameDetailViewModel>(relaxed = true)
        every { viewModel.uiState } returns flow
        composeRule.setContent {
            PfpScreenPreview {
                GameDetailScreen(
                    gameId = 1L,
                    onBack = {},
                    showTouchControls = false,
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * Root-space Y of the action row, used as the page's scroll instrument.
     *
     * Details, not Play: "Play" also appears as the helper footer's Confirm label when the cursor
     * is on the launch button, and a two-node match would read whichever one came first.
     */
    private fun topBandTop(): Float =
        composeRule.onNodeWithText("Details").fetchSemanticsNode().boundsInRoot.top

    @Test
    fun `opening the page with Play focused renders the whole action row`() {
        render(MutableStateFlow(state(GameDetailKeys.LAUNCH)))

        composeRule.onNodeWithText("Details").assertIsDisplayed()
        // "Options" is only the helper footer's prompt now: the pill that used to carry it moved
        // into the Details dropdown, so a second match here means a pill came back.
        val options = composeRule.onAllNodesWithText("Options")
        options.assertCountEquals(1)
        options[0].assertIsDisplayed()
    }

    @Test
    fun `focus returning to the top band brings the page back to the artwork`() {
        val flow = MutableStateFlow(state(GameDetailKeys.LAUNCH))
        render(flow)
        val atTop = topBandTop()

        // Moving down to the information band scrolls the page; the top band must move up.
        //
        // The band, not a media tile: bring-into-view is a no-op for something already on screen,
        // and the redesigned page is short enough that the first media tile is visible from the
        // top. A test that focuses it measures a scroll that correctly never happened.
        composeRule.runOnIdle { flow.value = state(GameDetailKeys.INFO) }
        composeRule.waitForIdle()
        val scrolledDown = topBandTop()
        assertTrue(
            "focusing a media tile must scroll the page (top band moved from $atTop to $scrolledDown)",
            scrolledDown < atTop,
        )

        // Coming back must return the page to the top, not park it just above the action row:
        // the logo and the backdrop above it are not nodes, so nothing else can bring them back.
        composeRule.runOnIdle { flow.value = state(GameDetailKeys.LAUNCH) }
        composeRule.waitForIdle()
        assertTrue(
            "returning to the top band must restore the page top (expected ~$atTop, was ${topBandTop()})",
            kotlin.math.abs(topBandTop() - atTop) < 1f,
        )
    }
}
