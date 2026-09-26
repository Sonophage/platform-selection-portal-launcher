package com.psplauncher.feature.xmb.ui.detail

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h640dp")
class GameDetailScreenContentTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun render(state: GameDetailUiState) {
        val flow = MutableStateFlow(state)
        val viewModel = mockk<GameDetailViewModel>(relaxed = true)
        every { viewModel.uiState } returns flow

        composeRule.setContent {
            PfpScreenPreview {
                GameDetailScreen(
                    gameId = state.game?.id ?: 0L,
                    onBack = {},
                    showTouchControls = false,
                    viewModel = viewModel,
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun exists(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun assertAbsent(text: String, why: String) =
        assertTrue("$why — found \"$text\"", !exists(text))

    private fun assertPresent(text: String, why: String) =
        assertTrue("$why — \"$text\" missing", exists(text))

    @Test
    fun `a package-backed entry never offers emulator controls`() {
        render(
            GameDetailUiState(
                isLoading = false,
                game = Game(
                    id = 7L,
                    title = "Netflix",
                    platformId = "android",

                    romPath = null,
                    packageName = "com.netflix.mediaclient",
                ),
            ),
        )

        assertAbsent("Emulator", "a package-backed entry must not offer an emulator action")

        assertPresent("Play", "the primary action must survive an entry with no extra content")
        assertPresent("Favorite", "the footer's first button must survive a bare entry")
        assertPresent("Options", "the gear is where scrape and edit live, on every entry")
        assertPresent("Back", "the helper footer must document Back")
    }

    @Test
    fun `an entry with media and metadata shows every band`() {
        render(
            GameDetailUiState(
                isLoading = false,
                detailMedia = listOf(
                    DetailMedia("/tmp/clip.mp4", isVideo = true),
                    DetailMedia("/tmp/shot.png", isVideo = false),
                ),
                game = Game(
                    id = 1L,
                    title = "Crash Bandicoot",
                    platformId = "psx",
                    romPath = "/roms/psx/crash.bin",
                    releaseYear = 1996,
                    developer = "Naughty Dog",
                    description = "Bandicoot jumps.",
                ),
            ),
        )

        assertPresent("Crash Bandicoot", "the logo page falls back to the title when there is no logo")
        assertAbsent("Emulator", "the emulator field is an Options row now, not a page band")
        assertAbsent("Manual", "the manual row belongs to the closed Options menu")
        assertAbsent("VIDEO", "the media tiles belong to the media page, which is not the one showing")
        assertAbsent("Bandicoot jumps.", "the description belongs to the info page, not the logo page")

        assertPresent("Play", "the footer carries Play on every page")
        assertPresent("Options", "the footer carries the gear on every page")
    }

    @Test
    fun `missing content is a dead end that still offers a way back`() {
        render(GameDetailUiState(isLoading = false, game = null))

        composeRule.onNodeWithText("This game is no longer in your library.").assertExists()
        composeRule.onNodeWithText("Press Back to return to the library.").assertExists()
    }
}
