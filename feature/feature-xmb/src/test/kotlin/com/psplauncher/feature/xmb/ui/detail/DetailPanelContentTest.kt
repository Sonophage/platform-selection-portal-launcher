package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.core.domain.model.Game
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The panel is drawn for two different row types — the crossbar's [XMBItem] and the drill-down's
 * [Game] — and [DetailPanelContent] is the seam that keeps it from growing two renderers. A seam
 * is exactly the thing worth guarding: the two builders can drift apart silently, because each one
 * compiles perfectly well while disagreeing with the other about what a game is.
 */
class DetailPanelContentTest {

    private val game = Game(
        id = 1L,
        title = "Crash Bandicoot",
        platformId = "psx",
        logoUri = "file:///logo.png",
        boxArtUri = "file:///box.png",
        heroUri = "file:///hero.png",
        romPath = "/storage/roms/psx/Crash Bandicoot (USA).bin",
        description = "A marsupial runs right.",
        releaseYear = 1996,
        genre = "Platform",
        developer = "Naughty Dog",
        players = "1",
    )

    private val item = XMBItem(
        id = "1",
        title = "Crash Bandicoot",
        platformId = "psx",
        logoUri = "file:///logo.png",
        boxArtUri = "file:///box.png",
        heroUri = "file:///hero.png",
        romPath = "/storage/roms/psx/Crash Bandicoot (USA).bin",
        description = "A marsupial runs right.",
        metadataLine = "1996   ·   Platform   ·   Naughty Dog   ·   1 player",
    )

    @Test
    fun `both hosts describe the same game the same way`() {
        val fromGame = detailPanelContentFor(game, "PlayStation", media = emptyList())
        val fromItem = detailPanelContentFor(item, "PlayStation")

        // Everything the panel actually draws, field by field. The point is not that the two
        // builders exist — it is that a change to one and not the other is caught here rather
        // than showing up as the crossbar and the detail page naming different things.
        assertEquals(fromGame.title, fromItem.title)
        assertEquals(fromGame.logoUri, fromItem.logoUri)
        assertEquals(fromGame.boxArtUri, fromItem.boxArtUri)
        assertEquals(fromGame.posterFallbackUri, fromItem.posterFallbackUri)
        assertEquals(fromGame.description, fromItem.description)
        assertEquals(fromGame.fileName, fromItem.fileName)
        assertEquals(fromGame.metaLine, fromItem.metaLine)
    }

    @Test
    fun `the filename is the file, not the path`() {
        // The panel's footer line in the reference is "Pokemon Unbound [v2.1.1.1].gba", not the
        // storage path it sits under.
        assertEquals(
            "Crash Bandicoot (USA).bin",
            detailPanelContentFor(game, "PlayStation", emptyList()).fileName,
        )
    }

    @Test
    fun `a package-backed entry has no filename rather than an empty one`() {
        // Android and Windows entries have no romPath at all. An empty string would draw a blank
        // line in the card; null draws nothing.
        val app = game.copy(romPath = null)

        assertNull(detailPanelContentFor(app, "Android", emptyList()).fileName)
        assertNull(panelFileName(""))
        assertNull(panelFileName("/trailing/slash/"))
    }

    @Test
    fun `the crossbar never offers the media page`() {
        // Not an oversight to be fixed later: resolving a game's media touches the disk, and the
        // crossbar would do it on every D-pad press. If this ever starts passing media, that
        // decision is being reversed and should be reversed deliberately.
        val content = detailPanelContentFor(item, "PlayStation")

        assertTrue(content.media.isEmpty())
        assertTrue(DetailPanelPage.GALLERY !in content.pages)
    }

    @Test
    fun `the video page follows the clip, on both hosts`() {
        // The crossbar gets the snap it already had approved; the drill-down gets the game's
        // resolved video. Neither offers the tab without one.
        assertTrue(DetailPanelPage.VIDEO !in detailPanelContentFor(item, "PlayStation").pages)
        assertTrue(
            DetailPanelPage.VIDEO in
                detailPanelContentFor(item, "PlayStation", videoUri = "file:///snap.mp4").pages,
        )
        assertTrue(
            DetailPanelPage.VIDEO in
                detailPanelContentFor(game, "PlayStation", emptyList(), videoUri = "file:///snap.mp4").pages,
        )
    }

    @Test
    fun `the drill-down offers the media page once there is media`() {
        val content = detailPanelContentFor(
            game,
            "PlayStation",
            media = listOf(DetailMedia(uri = "file:///shot.png", isVideo = false)),
        )

        assertTrue(DetailPanelPage.GALLERY in content.pages)
    }

    @Test
    fun `the info tab goes when the game filled nothing in`() {
        // No description, no scraped line, no file on disk: the card would be one sentence
        // saying there is no description. The rail should not offer it.
        val bare = item.copy(description = null, metadataLine = null, romPath = null)
        val content = detailPanelContentFor(bare, "PlayStation")

        assertTrue(DetailPanelPage.INFO !in content.pages)
        // One filled field is enough to be worth opening.
        assertTrue(
            DetailPanelPage.INFO in detailPanelContentFor(bare.copy(romPath = "/r/x.bin"), "PS").pages,
        )
    }

    @Test
    fun `a logo that will never be drawn is not offered as a logo`() {
        // hasVisibleLogo needs backdrop art as well as a logo. Without it the crossbar draws no
        // logo at all, so the content must not claim one — the shell reads this same field to
        // drive the PIC0 fade.
        val orphanLogo = item.copy(artworkUri = null, heroUri = null, boxArtUri = null)

        assertNull(detailPanelContentFor(orphanLogo, "PlayStation").logoUri)
    }

    @Test
    fun `a game never launched from here shows no play time at all`() {
        // Null, not "0 min". Only 2 of the owner's 148 games have a recorded time, because the
        // column is written on return from a launch; a library of zeroes would read as a broken
        // counter rather than as one that has not been played through this launcher yet.
        assertNull(panelPlayTime(0L))
        assertNull(panelPlayTime(-1L))
        assertEquals("Under a minute", panelPlayTime(30_000L))
        assertEquals("45 min", panelPlayTime(45 * 60_000L))
        assertEquals("2 h 5 min", panelPlayTime((2 * 60 + 5) * 60_000L))
    }

    @Test
    fun `a row with no box art cannot be walked onto a box art page`() {
        // This is what the shoulders read. A page list that disagreed with the row's artwork
        // would let L1 R1 land the user on an empty panel.
        val noArt = detailPanelContentFor(item.copy(boxArtUri = null), "PlayStation")

        assertEquals(listOf(DetailPanelPage.LOGO, DetailPanelPage.INFO), noArt.pages)
        assertEquals(DetailPanelPage.INFO, stepPanelPage(DetailPanelPage.LOGO, noArt.pages, +1))
    }
}
