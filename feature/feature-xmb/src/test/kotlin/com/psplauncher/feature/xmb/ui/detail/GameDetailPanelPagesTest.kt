package com.psplauncher.feature.xmb.ui.detail

import org.junit.Assert.assertEquals
import org.junit.Test

class GameDetailPanelPagesTest {
    @Test
    fun `a game with no art still offers the logo and whatever it can say`() {
        val pages = availablePanelPages(hasBoxArt = false, hasVideo = false, hasGallery = false, hasInfo = true)

        assertEquals(listOf(DetailPanelPage.LOGO, DetailPanelPage.INFO), pages)
    }

    @Test
    fun `a game that scraped nothing at all is offered nothing but the resting page`() {
        val pages = availablePanelPages(
            hasBoxArt = false, hasVideo = false, hasGallery = false, hasInfo = false,
        )

        assertEquals(listOf(DetailPanelPage.LOGO), pages)
        assertEquals(DetailPanelPage.LOGO, stepPanelPage(DetailPanelPage.LOGO, pages, +1))
        assertEquals(DetailPanelPage.LOGO, stepPanelPage(DetailPanelPage.LOGO, pages, -1))
    }

    @Test
    fun `the logo page is never dropped, however bare the game`() {
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
        val pages = availablePanelPages(hasBoxArt = false, hasVideo = false, hasGallery = true, hasInfo = false)

        assertEquals(DetailPanelPage.GALLERY, stepPanelPage(DetailPanelPage.LOGO, pages, +1))
        assertEquals(DetailPanelPage.LOGO, stepPanelPage(DetailPanelPage.GALLERY, pages, -1))
    }

    @Test
    fun `a page that vanishes under the cursor cannot be walked into a garbage index`() {
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
