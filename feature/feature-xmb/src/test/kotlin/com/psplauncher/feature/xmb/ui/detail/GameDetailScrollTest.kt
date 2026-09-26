package com.psplauncher.feature.xmb.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.ui.preview.PfpScreenPreview
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

        composeRule.onAllNodesWithText("Play").assertCountEquals(2)
        composeRule.onAllNodesWithText("Favorite").assertCountEquals(1)
        composeRule.onAllNodesWithText("Options").assertCountEquals(2)
    }
}
