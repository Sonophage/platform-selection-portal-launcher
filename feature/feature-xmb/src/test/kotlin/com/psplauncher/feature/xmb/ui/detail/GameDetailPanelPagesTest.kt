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
    fun `a game with no art still offers the logo and whatever it can say`() {
        val pages = availablePanelPages(hasBoxArt = false, hasVideo = false, hasGallery = false, hasInfo = true)

        assertEquals(listOf(DetailPanelPage.LOGO, DetailPanelPage.INFO), pages)
    }

    @Test
    fun `a game that scraped nothing at all is offered nothing but the resting page`() {
        // A homebrew with no metadata, no description and no file on disk. Every tab but the
        // logo would open on an empty panel, so the strip has one cell and L1 R1 do nothing.
        val pages = availablePanelPages(
            hasBoxArt = false, hasVideo = false, hasGallery = false, hasInfo = false,
        )

        assertEquals(listOf(DetailPanelPage.LOGO), pages)
        assertEquals(DetailPanelPage.LOGO, stepPanelPage(DetailPanelPage.LOGO, pages, +1))
        assertEquals(DetailPanelPage.LOGO, stepPanelPage(DetailPanelPage.LOGO, pages, -1))
    }

    @Test
    fun `the logo page is never dropped, however bare the game`() {
        // Not an oversight in the filter: it is the resting state, so dropping it would leave no
        // way back to the plain crossbar once the user has walked off it, and would pop box art
        // onto the screen unprompted for every game that has no logo.
        listOf(true, false).forEach { hasBoxArt ->
            listOf(true, false).forEach { hasInfo ->
                assertEquals(
                    DetailPanelPage.LOGO,
                    availablePanelPages(hasBoxArt, hasVideo = false, hasGallery = false, hasInfo = hasInfo)
                        .first(),
                )
            }
        }
    }

    @Test
    fun `an asset page appears only when its asset does`() {
        assertEquals(
            listOf(DetailPanelPage.LOGO, DetailPanelPage.INFO, DetailPanelPage.BOX_ART),
            availablePanelPages(hasBoxArt = true, hasVideo = false, hasGallery = false, hasInfo = true),
        )
        assertEquals(
            listOf(DetailPanelPage.LOGO, DetailPanelPage.INFO, DetailPanelPage.GALLERY),
            availablePanelPages(hasBoxArt = false, hasVideo = false, hasGallery = true, hasInfo = true),
        )
        assertEquals(
            listOf(
                DetailPanelPage.LOGO,
                DetailPanelPage.INFO,
                DetailPanelPage.BOX_ART,
                DetailPanelPage.GALLERY,
            ),
            availablePanelPages(hasBoxArt = true, hasVideo = false, hasGallery = true, hasInfo = true),
        )
        assertEquals(
            DetailPanelPage.entries.toList(),
            availablePanelPages(hasBoxArt = true, hasVideo = true, hasGallery = true, hasInfo = true),
        )
    }

    @Test
    fun `the strip is drawn in enum order whatever drops out of it`() {
        // Info is declared before Box Art, and a filter must not be allowed to reorder them:
        // the strip's icons and the L1 R1 walk have to agree with each other.
        val pages = availablePanelPages(hasBoxArt = true, hasVideo = false, hasGallery = true, hasInfo = true)

        assertEquals(pages.sortedBy { it.ordinal }, pages)
    }

    @Test
    fun `walking clamps at both ends instead of wrapping`() {
        val pages = availablePanelPages(hasBoxArt = true, hasVideo = false, hasGallery = true, hasInfo = true)

        assertEquals(
            "L1 on the first page stays put; wrapping would throw a visible highlight the full width",
            DetailPanelPage.LOGO,
            stepPanelPage(DetailPanelPage.LOGO, pages, -1),
        )
        assertEquals(
            "R1 on the last page stays put",
            DetailPanelPage.GALLERY,
            stepPanelPage(DetailPanelPage.GALLERY, pages, +1),
        )
    }

    @Test
    fun `the video page appears only when there is a clip to play`() {
        // It is the snap the crossbar already had approved, so a game with no video must not be
        // offered a tab that would open an empty player.
        assertEquals(
            listOf(DetailPanelPage.LOGO, DetailPanelPage.INFO, DetailPanelPage.VIDEO),
            availablePanelPages(hasBoxArt = false, hasVideo = true, hasGallery = false, hasInfo = true),
        )
        assertEquals(
            listOf(DetailPanelPage.LOGO, DetailPanelPage.INFO),
            availablePanelPages(hasBoxArt = false, hasVideo = false, hasGallery = false, hasInfo = true),
        )
    }

    @Test
    fun `walking skips the pages that are not offered`() {
        // A scraped-art game that filled in no text: Info is declared second but is not offered,
        // so R1 from Logo must land on Media rather than on a tab the strip is not drawing.
        val pages = availablePanelPages(hasBoxArt = false, hasVideo = false, hasGallery = true, hasInfo = false)

        assertEquals(DetailPanelPage.GALLERY, stepPanelPage(DetailPanelPage.LOGO, pages, +1))
        assertEquals(DetailPanelPage.LOGO, stepPanelPage(DetailPanelPage.GALLERY, pages, -1))
    }

    @Test
    fun `a page that vanishes under the cursor cannot be walked into a garbage index`() {
        // Open on Media, then the media resolves to nothing on reload. indexOf returns -1 and a
        // naive (index + delta) would index -1 or 0 and silently show the wrong page.
        val pages = availablePanelPages(hasBoxArt = false, hasVideo = false, hasGallery = false, hasInfo = true)

        assertEquals(DetailPanelPage.GALLERY, stepPanelPage(DetailPanelPage.GALLERY, pages, +1))
        assertEquals(DetailPanelPage.GALLERY, stepPanelPage(DetailPanelPage.GALLERY, pages, -1))
    }

    @Test
    fun `an unavailable page resolves to the first one the strip actually draws`() {
        val pages = availablePanelPages(hasBoxArt = false, hasVideo = false, hasGallery = false, hasInfo = true)

        assertEquals(DetailPanelPage.LOGO, resolvePanelPage(DetailPanelPage.BOX_ART, pages))
        assertEquals(
            "a page that IS available is never moved",
            DetailPanelPage.INFO,
            resolvePanelPage(DetailPanelPage.INFO, pages),
        )
    }
}
