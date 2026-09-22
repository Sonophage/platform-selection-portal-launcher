package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.feature.xmb.ui.FocusedGameVideo
import com.psplauncher.feature.xmb.ui.detail.DetailPanelPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [XMBUiState.hoverPanelContent] has two consumers that must never disagree: XMBShell draws the
 * panel and its tab strip, and stepHoverPanelPage decides where L1/R1 land.
 *
 * They already did disagree once, on the device, in the way this file exists to prevent. The
 * shell built the content with the approved video snap and the shoulder walk built its own
 * without one, so the strip drew a Video tab and R1 stepped straight over it onto Info. Both
 * halves compiled, both were "correct", and the only symptom was a button skipping a tab.
 */
class HoverPanelContentTest {

    private val game = XMBItem(
        id = "7",
        title = "Alice in Wonderland",
        gameId = 7L,
        platformId = "gbc",
        isRealGame = true,
        logoUri = "file:///logo.png",
        boxArtUri = "file:///box.png",
        // backdropArt is derived from these, and the panel is gated on it being non-empty.
        artworkUri = "file:///art.png",
    )

    private fun state(vararg items: XMBItem) = XMBUiState(currentItems = items.toList())

    @Test
    fun `the page list the strip draws is the page list the shoulders walk`() {
        // The actual regression. One property now answers both, so the assertion is that the
        // video the shell knows about is in the list the walk reads — not that two builders
        // happen to agree today.
        val s = state(game).copy(
            focusedGameVideo = FocusedGameVideo(gameId = 7L, uri = "file:///snap.mp4"),
        )

        val pages = s.hoverPanelContent!!.pages
        assertTrue("the strip draws a Video tab, so the walk must be able to land on it",
            DetailPanelPage.VIDEO in pages)
        // This game fills in no text, so its strip is Logo, Video, Box Art and the step that
        // proves the point starts at Logo. Stepping from a page the strip is NOT drawing would
        // return it unchanged and the assertion would pass for the wrong reason.
        assertEquals(
            "R1 from the logo lands on Video, not over it",
            DetailPanelPage.VIDEO,
            com.psplauncher.feature.xmb.ui.detail.stepPanelPage(DetailPanelPage.LOGO, pages, +1),
        )
    }

    @Test
    fun `the page returns to the logo when the cursor moves to another game`() {
        // The panel is a thing you opened on ONE game. Carrying it to the next row would mean
        // walking a list with a box front permanently in the way, and the crossbar should look
        // like the crossbar until you ask otherwise.
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
        // The default state: panelPage is LOGO and panelPageGameId is null. A non-null page with
        // a null id could only come from a partial write, and must not stick to every row.
        val orphan = XMBUiState(
            currentItems = listOf(game),
            panelPage = DetailPanelPage.BOX_ART,
            panelPageGameId = null,
        )

        assertEquals(DetailPanelPage.LOGO, orphan.effectivePanelPage)
    }

    @Test
    fun `a snap approved for a different row is not this row's video`() {
        // focusedGameVideo is a single slot on the state. Reading it without checking the id
        // would hand one game's clip to whatever the cursor is on.
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
        // The region has always needed backdrop art; a card on the bare wallpaper reads as a
        // stray. backdropArt is derived, so this clears every source of it.
        val bare = game.copy(artworkUri = null, heroUri = null, boxArtUri = null, iconUri = null)

        assertNull(state(bare).hoverPanelContent)
    }
}
