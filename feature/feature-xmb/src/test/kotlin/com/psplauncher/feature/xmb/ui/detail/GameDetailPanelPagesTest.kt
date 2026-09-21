package com.psplauncher.feature.xmb.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The panel strip is what L1/R1 walk, and the whole point of keeping it a pure function is that
 * the walk can be proven without a composition or a device.
 *
 * What these encode is why the rules are what they are, not that a list has four entries: a tab
 * the user can reach and find empty is a worse answer than a tab that is not offered, and a strip
 * the user can see must not teleport its highlight.
 */
class GameDetailPanelPagesTest {

    @Test
    fun `a game with no art still offers the two pages that always have something to say`() {
        val pages = availablePanelPages(hasBoxArt = false, hasGallery = false)

        // Not "size == 2": which two matters. Logo falls back to the title and Info to the
        // filename, so neither can be empty; the other two would be.
        assertEquals(listOf(DetailPanelPage.LOGO, DetailPanelPage.INFO), pages)
    }

    @Test
    fun `an asset page appears only when its asset does`() {
        assertEquals(
            listOf(DetailPanelPage.LOGO, DetailPanelPage.BOX_ART, DetailPanelPage.INFO),
            availablePanelPages(hasBoxArt = true, hasGallery = false),
        )
        assertEquals(
            listOf(DetailPanelPage.LOGO, DetailPanelPage.GALLERY, DetailPanelPage.INFO),
            availablePanelPages(hasBoxArt = false, hasGallery = true),
        )
        assertEquals(
            DetailPanelPage.entries.toList(),
            availablePanelPages(hasBoxArt = true, hasGallery = true),
        )
    }

    @Test
    fun `the strip is drawn in enum order whatever drops out of it`() {
        // Box art is declared before Gallery, and a filter must not be allowed to reorder them:
        // the strip's icons and the L1 R1 walk have to agree with each other.
        val pages = availablePanelPages(hasBoxArt = true, hasGallery = true)

        assertEquals(pages.sortedBy { it.ordinal }, pages)
    }

    @Test
    fun `walking clamps at both ends instead of wrapping`() {
        val pages = availablePanelPages(hasBoxArt = true, hasGallery = true)

        assertEquals(
            "L1 on the first page stays put; wrapping would throw a visible highlight the full width",
            DetailPanelPage.LOGO,
            stepPanelPage(DetailPanelPage.LOGO, pages, -1),
        )
        assertEquals(
            "R1 on the last page stays put",
            DetailPanelPage.INFO,
            stepPanelPage(DetailPanelPage.INFO, pages, +1),
        )
    }

    @Test
    fun `walking skips the pages that are not offered`() {
        // The real case: a game with no box art. R1 from Logo must land on Media, not on a box
        // art page that the strip is not drawing.
        val pages = availablePanelPages(hasBoxArt = false, hasGallery = true)

        assertEquals(DetailPanelPage.GALLERY, stepPanelPage(DetailPanelPage.LOGO, pages, +1))
        assertEquals(DetailPanelPage.LOGO, stepPanelPage(DetailPanelPage.GALLERY, pages, -1))
    }

    @Test
    fun `a page that vanishes under the cursor cannot be walked into a garbage index`() {
        // Open on Media, then the media resolves to nothing on reload. indexOf returns -1 and a
        // naive (index + delta) would index -1 or 0 and silently show the wrong page.
        val pages = availablePanelPages(hasBoxArt = false, hasGallery = false)

        assertEquals(DetailPanelPage.GALLERY, stepPanelPage(DetailPanelPage.GALLERY, pages, +1))
        assertEquals(DetailPanelPage.GALLERY, stepPanelPage(DetailPanelPage.GALLERY, pages, -1))
    }

    @Test
    fun `an unavailable page resolves to the first one the strip actually draws`() {
        val pages = availablePanelPages(hasBoxArt = false, hasGallery = false)

        assertEquals(DetailPanelPage.LOGO, resolvePanelPage(DetailPanelPage.BOX_ART, pages))
        assertEquals(
            "a page that IS available is never moved",
            DetailPanelPage.INFO,
            resolvePanelPage(DetailPanelPage.INFO, pages),
        )
    }
}
