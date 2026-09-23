package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Video Detail's prompt row, which is now also its touch surface.
 *
 * This screen kept two large floating pills long after every other page had folded its touch
 * controls into the prompt row, because it had no row to fold them into. It has one now, and that
 * makes the row load-bearing in a way a legend never was: a prompt that cannot be tapped is a
 * button a touch user cannot press, and a prompt naming an action the dispatcher ignores in that
 * context is a button that does nothing. Both are invisible on a screenshot.
 *
 * [videoDetailHelperItems] is pure for that reason — the contract is checkable without a device.
 */
class VideoDetailHelperFooterTest {

    private val video = Video(
        id = "v1",
        libraryId = "lib",
        uri = "content://v1",
        displayName = "Dune.mkv",
        title = "Dune",
    )

    private val baseState = VideoDetailUiState(isLoading = false, video = video)

    private fun labels(state: VideoDetailUiState) = videoDetailHelperItems(state).map { it.label }

    @Test
    fun `every prompt on this row can actually be pressed`() {
        // The whole reason the pills could go. ControllerPromptItem.tappableAction() returns null
        // for a prompt naming two actions, and such a prompt renders as a legend a finger cannot
        // use — which on this screen would mean no way to leave the page by touch at all.
        //
        // The D-pad "Navigate" hint is the deliberate exception: it is a fixed glyph describing a
        // direction, not an action anything could fire.
        val states = listOf(
            baseState,
            baseState.copy(showOptions = true),
            baseState.copy(showPlaylistPicker = true),
            baseState.copy(confirmRemove = true),
            baseState.copy(isEditingTitle = true),
            baseState.copy(creatingPlaylist = true),
            baseState.copy(infoVisible = true),
            baseState.copy(launchError = "no player"),
        )
        states.forEach { state ->
            videoDetailHelperItems(state).forEach { item ->
                if (item.fixedIcons != null) return@forEach
                assertNotNull(
                    "\"${item.label}\" names ${item.actions.size} actions, so touch cannot fire it",
                    item.tappableAction(),
                )
            }
        }
    }

    @Test
    fun `the base row is Play, Options and Back`() {
        assertEquals(listOf("Play", "Options", "Back"), labels(baseState))
        assertEquals(
            listOf(
                listOf(GamepadAction.SELECT),
                listOf(GamepadAction.OPEN_CONTEXT_MENU),
                listOf(GamepadAction.BACK),
            ),
            videoDetailHelperItems(baseState).map { it.actions },
        )
    }

    @Test
    fun `a part-watched film says Resume, because that is what the button does`() {
        // The lead prompt is read off primaryActions rather than hardcoded, so the row cannot say
        // "Play" while the button under it restarts from a saved position — the page already
        // decides Play vs Resume once, and this reads that decision instead of repeating it.
        val resuming = baseState.copy(video = video.copy(resumePositionMs = 90_000))
        assertEquals("Resume", labels(resuming).first())
        assertEquals("Play", labels(baseState).first())
    }

    @Test
    fun `an overlay documents the overlay, not the page under it`() {
        // Back means "close this", not "leave the film", whenever something is over the page. The
        // dispatcher already works that way; before the footer existed nothing said so.
        assertEquals(listOf("Navigate", "Select", "Close"), labels(baseState.copy(showOptions = true)))
        assertEquals(listOf("Navigate", "Select", "Close"), labels(baseState.copy(showPlaylistPicker = true)))
        assertEquals(listOf("Close"), labels(baseState.copy(infoVisible = true)))
        assertEquals(listOf("Remove", "Cancel"), labels(baseState.copy(confirmRemove = true)))
        assertEquals(listOf("Cancel"), labels(baseState.copy(isEditingTitle = true)))
    }

    @Test
    fun `a launch error offers only the way out of it`() {
        // handleGamepadAction swallows everything but SELECT and BACK while launchError is set, so
        // a row offering Options there would be three buttons of which one works.
        val errored = baseState.copy(launchError = "No player app can open this file")
        assertEquals(listOf("Dismiss"), labels(errored))
        assertTrue(videoDetailHelperItems(errored).all { it.tappableAction() != null })
    }

    @Test
    fun `the error row wins over an overlay that is also open`() {
        // The error dialog draws above the Options menu and takes input from it. If the branch
        // order flipped, the row would document the menu while the dialog held the buttons.
        assertEquals(
            listOf("Dismiss"),
            labels(baseState.copy(showOptions = true, launchError = "boom")),
        )
    }
}
