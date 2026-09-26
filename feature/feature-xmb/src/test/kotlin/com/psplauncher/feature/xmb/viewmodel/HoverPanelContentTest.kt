package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.feature.xmb.ui.FocusedGameVideo
import com.psplauncher.feature.xmb.ui.detail.DetailPanelPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HoverPanelContentTest {
    private val game = XMBItem(
        id = "7",
        title = "Alice in Wonderland",
        gameId = 7L,
        platformId = "gbc",
        isRealGame = true,
        logoUri = "file:///logo.png",
        boxArtUri = "file:///box.png",

        artworkUri = "file:///art.png",
    )

    private fun state(vararg items: XMBItem) = XMBUiState(currentItems = items.toList())

    @Test
    fun `the page list the strip draws is the page list the shoulders walk`() {
        val s = state(game).copy(
            focusedGameVideo = FocusedGameVideo(gameId = 7L, uri = "file:///snap.mp4"),
        )

        val pages = s.hoverPanelContent!!.pages
        assertTrue("the strip draws a Video tab, so the walk must be able to land on it",
            DetailPanelPage.VIDEO in pages)

        assertEquals(
            "R1 from the logo lands on Video, not over it",
            DetailPanelPage.VIDEO,
            com.psplauncher.feature.xmb.ui.detail.stepPanelPage(DetailPanelPage.LOGO, pages, +1),
        )
    }

    @Test
    fun `the page returns to the logo when the cursor moves to another game`() {
        val other = game.copy(id = "8", title = "Another", gameId = 8L)
        val walked = XMBUiState(
            currentItems = listOf(game, other),
            selectedItemIndex = 0,
            panelPage = DetailPanelPage.BOX_ART,
            panelPageGameId = 7L,
        )

        assertEquals(DetailPanelPage.BOX_ART, walked.effectivePanelPage)
        assertEquals(
            "moving the cursor to the next row puts the panel back on its logo page",
            DetailPanelPage.LOGO,
            walked.copy(selectedItemIndex = 1).effectivePanelPage,
        )
    }

    @Test
    fun `a page with no game attached to it is not honoured`() {
        val orphan = XMBUiState(
            currentItems = listOf(game),
            panelPage = DetailPanelPage.BOX_ART,
            panelPageGameId = null,
        )

        assertEquals(DetailPanelPage.LOGO, orphan.effectivePanelPage)
    }

    @Test
    fun `a snap approved for a different row is not this row's video`() {
        val s = state(game).copy(
            focusedGameVideo = FocusedGameVideo(gameId = 99L, uri = "file:///other.mp4"),
        )

        assertNull(s.hoverPanelContent!!.videoUri)
        assertTrue(DetailPanelPage.VIDEO !in s.hoverPanelContent!!.pages)
    }

    @Test
    fun `there is no panel for a row that is not a game`() {
        val row = game.copy(isRealGame = false)

        assertNull(state(row).hoverPanelContent)
    }

    @Test
    fun `there is no panel for a game with nothing behind it`() {
        val bare = game.copy(artworkUri = null, heroUri = null, boxArtUri = null, iconUri = null)

        assertNull(state(bare).hoverPanelContent)
    }
}
