package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which rows may colour the shell, and from which picture.
 *
 * This is the one rule the whole crossbar's appearance now hangs on. Games had it and the other
 * libraries did not, so the shell took a game's colour and then snapped back to the flat theme on
 * Music or Photo. Making it a property of the ROW rather than of the category is what fixes that,
 * and it is also what makes the two ways of getting it wrong possible: letting the shared folder
 * icon through, so every navigation row tints the shell the same grey, or dropping a library's
 * art field, so that library silently goes back to the flat theme.
 */
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
        // coverUri is what Music, Video, Photo and Books all fill. Leaving it out of the list is
        // the exact mistake that had those categories fall back to the flat theme.
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
        // It is the same bundled PNG on the "Music", "Photos" and "All Videos" rows. Read as art,
        // it would tint the shell one grey on every folder in every category, which reads as the
        // theme having broken rather than as a colour being taken from something.
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
        // A video library carries its own artwork; only the bundled default is excluded, not the
        // whole idea of a folder having a picture.
        val library = XMBItem(id = "lib1", title = "Movies", coverUri = "content://library-art")
        assertEquals(listOf("content://library-art"), library.backdropArt)
    }

    // ── The logo / label pair ──────────────────────────────────────────────────
    //
    // Two conditions that must agree, where only one was guarded. The row hid its title whenever a
    // logo existed; the shell only drew the logo when there was background art too. A game with a
    // logo and no art got neither, and nothing logged. Both sites read hasVisibleLogo now.

    @Test
    fun `a logo with no artwork behind it is not a visible logo`() {
        // The exact hole: this row used to be nameless. No label, because it has a logo; no logo,
        // because there is nothing to draw it over.
        val orphanLogo = XMBItem(id = "g1", title = "Crash", logoUri = "content://logo", isRealGame = true)
        assertTrue(orphanLogo.backdropArt.isEmpty())
        assertTrue("a row with no art must keep its title", !orphanLogo.hasVisibleLogo)
    }

    @Test
    fun `a logo with any readable art behind it is a visible logo`() {
        // Any candidate, not artworkUri specifically: the shell shows the first one that decodes,
        // so the logo has to ask the same question or the two disagree again.
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
        // A blank string is not a logo either — a scraper that wrote "" must not blank the title.
        assertTrue(!XMBItem(id = "g3", title = "x", logoUri = "  ", artworkUri = "content://bg").hasVisibleLogo)
    }

    @Test
    fun `a row with no art at all colours nothing`() {
        // Settings rows, text-only rows and unscraped games: the shell keeps the user's theme,
        // which is what it did before any of this existed.
        assertEquals(emptyList<String>(), XMBItem(id = "settings_library", title = "Settings").backdropArt)
        assertEquals(emptyList<String>(), XMBItem(id = "x", title = "X", artworkUri = "   ").backdropArt)
    }
}
