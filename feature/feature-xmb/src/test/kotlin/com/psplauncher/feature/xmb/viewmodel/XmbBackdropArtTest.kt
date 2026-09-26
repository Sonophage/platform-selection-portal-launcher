package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmbBackdropArtTest {
    @Test
    fun `a game reads its background slot first, then its hero`() {
        val game = XMBItem(
            id = "g1",
            title = "Crash",
            artworkUri = "content://bg",
            heroUri = "content://hero",
            boxArtUri = "content://box",
            isRealGame = true,
        )
        assertEquals(listOf("content://bg", "content://hero", "content://box"), game.backdropArt)
    }

    @Test
    fun `every library's own art field is read, not just a game's`() {
        listOf(
            XMBItem(id = "t1", title = "Track", coverUri = "content://album", type = XMBItemType.MUSIC_TRACK),
            XMBItem(id = "v1", title = "Clip", coverUri = "content://thumb", type = XMBItemType.VIDEO_FILE),
            XMBItem(id = "p1", title = "Photo", coverUri = "content://photo", type = XMBItemType.PHOTO_FILE),
        ).forEach { row ->
            assertTrue("${row.id} must be able to colour the shell", row.backdropArt.isNotEmpty())
        }
    }

    @Test
    fun `the shared folder icon never colours the shell`() {
        val folder = XMBItem(
            id = "all_music",
            title = "Music",
            coverUri = XMBViewModel.MEMORY_CARD_ASSET_URI,
            type = XMBItemType.MEMORY_CARD,
        )
        assertEquals(emptyList<String>(), folder.backdropArt)
    }

    @Test
    fun `a folder row with real art of its own still counts`() {
        val library = XMBItem(id = "lib1", title = "Movies", coverUri = "content://library-art")
        assertEquals(listOf("content://library-art"), library.backdropArt)
    }

    @Test
    fun `a logo with no artwork behind it is not a visible logo`() {
        val orphanLogo = XMBItem(id = "g1", title = "Crash", logoUri = "content://logo", isRealGame = true)
        assertTrue(orphanLogo.backdropArt.isEmpty())
        assertTrue("a row with no art must keep its title", !orphanLogo.hasVisibleLogo)
    }

    @Test
    fun `a logo with any readable art behind it is a visible logo`() {
        listOf(
            XMBItem(id = "a", title = "x", logoUri = "content://l", artworkUri = "content://bg"),
            XMBItem(id = "b", title = "x", logoUri = "content://l", heroUri = "content://hero"),
            XMBItem(id = "c", title = "x", logoUri = "content://l", boxArtUri = "content://box"),
        ).forEach { assertTrue("${it.id} should draw its logo", it.hasVisibleLogo) }
    }

    @Test
    fun `no logo is never a visible logo, however much art there is`() {
        val noLogo = XMBItem(id = "g2", title = "Crash", artworkUri = "content://bg", isRealGame = true)
        assertTrue(!noLogo.hasVisibleLogo)

        assertTrue(!XMBItem(id = "g3", title = "x", logoUri = "  ", artworkUri = "content://bg").hasVisibleLogo)
    }

    @Test
    fun `a row with no art at all colours nothing`() {
        assertEquals(emptyList<String>(), XMBItem(id = "settings_library", title = "Settings").backdropArt)
        assertEquals(emptyList<String>(), XMBItem(id = "x", title = "X", artworkUri = "   ").backdropArt)
    }
}
