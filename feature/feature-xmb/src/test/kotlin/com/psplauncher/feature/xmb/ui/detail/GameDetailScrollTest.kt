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

    @Test
    fun `every panel page renders without the scroll crash`() {
        // What this file has always really guarded. The page's scroll effect used to branch on
        // ScrollState.animateScrollTo, and opening the page died with
        // "ClassCastException: Float cannot be cast to kotlin.Unit". Rendering is the test.
        //
        // The distance assertions that used to sit here measured a scrolling body: Overview at
        // the top, the information band at the bottom, and the page moving between them. The body
        // is a panel now — one page at a time, sized to the viewport — so there is nothing to
        // scroll and a test measuring how far it scrolled would be measuring zero and calling it
        // a pass. Each page is rendered instead, which is the failure mode that remains.
        // One composition, walked through the pages: the rule allows setContent once.
        val flow = MutableStateFlow(state(GameDetailKeys.LAUNCH))
        render(flow)
        DetailPanelPage.entries.forEach { page ->
            composeRule.runOnIdle { flow.value = flow.value.copy(panelPage = page) }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `the footer carries Play and the two buttons beside it`() {
        render(MutableStateFlow(state(GameDetailKeys.LAUNCH)))

        // "Play" matches twice — the launch button and the helper footer's Confirm prompt, which
        // is labelled for what Confirm does. Same for "Options" and the gear. Counting is the
        // assertion: zero would mean the button never rendered.
        composeRule.onAllNodesWithText("Play").assertCountEquals(2)
        composeRule.onAllNodesWithText("Favourite").assertCountEquals(1)
        composeRule.onAllNodesWithText("Options").assertCountEquals(2)
    }
}
