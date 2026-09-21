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
    fun `the drill-down offers the media page once there is media`() {
        val content = detailPanelContentFor(
            game,
            "PlayStation",
            media = listOf(DetailMedia(uri = "file:///shot.png", isVideo = false)),
        )

        assertTrue(DetailPanelPage.GALLERY in content.pages)
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
