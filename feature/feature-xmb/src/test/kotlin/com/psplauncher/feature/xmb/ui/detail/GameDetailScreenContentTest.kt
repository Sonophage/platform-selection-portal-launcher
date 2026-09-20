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

/**
 * What the redesigned page renders for two content shapes the design calls out explicitly: a
 * package-backed entry (no emulator controls anywhere, no emulator information field) and an entry
 * with nothing to preview (no media band, no information band).
 *
 * Rendering the real screen with a state-driving fake is the point: the rules live in the layout, so
 * nothing short of a composition can show that a band is absent rather than empty.
 */
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

    /** Whether the composed page contains [text] anywhere (labels are not unique by design). */
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
                    // Package-backed: launched through its package, so no emulator applies.
                    romPath = null,
                    packageName = "com.netflix.mediaclient",
                ),
            ),
        )

        // The information band's inline emulator field is gone, not disabled.
        assertAbsent("Emulator", "a package-backed entry must not offer an emulator action")
        assertAbsent("MEDIA PREVIEW", "an entry with no media must not show a media band")
        // The page itself is still a page: Play, Details and its footer documentation are present.
        assertPresent("Play", "the primary action must survive an entry with no extra content")
        assertPresent("Details", "the secondary action must survive an entry with no extra content")
        assertPresent("Options", "the helper footer must document Options on the base page")
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

        // The emulator is changed from the information band's field, and the action row is now
        // only Play and Details: everything else lives inside the dropdown Details opens, which
        // is closed here, so none of its rows may be on the page.
        assertAbsent("Emulator", "the action row must not duplicate the emulator field")
        assertPresent("Details", "the action row's second button opens the dropdown")
        assertAbsent("Manual", "the manual row belongs to the closed dropdown, not to the page")
        assertAbsent("Favorite", "the favorite row belongs to the closed dropdown, not to the page")
        assertAbsent("Unavailable", "a missing manual is omitted, not explained")
        // The hero banner and the icon tile are gone: the art is the page's background now, and
        // the title is drawn by the logo block at the top left.
        assertPresent("Crash Bandicoot", "the logo block falls back to the title when there is no logo")
        assertPresent("MEDIA PREVIEW", "the media band appears once the strip has an asset")
        // The named plate on a playable tile: "this is playable" never rests on the glyph alone.
        assertPresent("VIDEO", "a video tile must be named, not just glyphed")
        // Field labels are uppercase chrome; the values keep their own casing.
        assertPresent("RELEASED", "the information band shows the values it has")
        assertPresent("1996", "the information band shows the values it has")
        assertPresent("Naughty Dog", "field values keep their own casing")
        assertAbsent("PUBLISHER", "absent values are omitted, never filled with Unknown")
    }

    @Test
    fun `missing content is a dead end that still offers a way back`() {
        render(GameDetailUiState(isLoading = false, game = null))

        composeRule.onNodeWithText("This game is no longer in your library.").assertExists()
        composeRule.onNodeWithText("Press Back to return to the library.").assertExists()
    }
}
