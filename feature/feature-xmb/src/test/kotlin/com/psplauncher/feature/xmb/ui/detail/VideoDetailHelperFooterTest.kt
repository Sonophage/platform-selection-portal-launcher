package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.domain.model.Video
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
        val resuming = baseState.copy(video = video.copy(resumePositionMs = 90_000))
        assertEquals("Resume", labels(resuming).first())
        assertEquals("Play", labels(baseState).first())
    }

    @Test
    fun `an overlay documents the overlay, not the page under it`() {
        assertEquals(listOf("Navigate", "Select", "Close"), labels(baseState.copy(showOptions = true)))
        assertEquals(listOf("Navigate", "Select", "Close"), labels(baseState.copy(showPlaylistPicker = true)))
        assertEquals(listOf("Close"), labels(baseState.copy(infoVisible = true)))
        assertEquals(listOf("Remove", "Cancel"), labels(baseState.copy(confirmRemove = true)))
        assertEquals(listOf("Cancel"), labels(baseState.copy(isEditingTitle = true)))
    }

    @Test
    fun `a launch error offers only the way out of it`() {
        val errored = baseState.copy(launchError = "No player app can open this file")
        assertEquals(listOf("Dismiss"), labels(errored))
        assertTrue(videoDetailHelperItems(errored).all { it.tappableAction() != null })
    }

    @Test
    fun `the error row wins over an overlay that is also open`() {
        assertEquals(
            listOf("Dismiss"),
            labels(baseState.copy(showOptions = true, launchError = "boom")),
        )
    }
}
