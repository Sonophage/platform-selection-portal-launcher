package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [XMBItem.backdropArt] and [XMBItem.shelfCoverArt] read the same five art slots in two different
 * orders, and nothing but this file keeps them level.
 *
 * They were not level. The home shelf's card was written when the shelf held games, listed four
 * slots, and left out coverUri — the ONLY slot a music track or a video row fills. Once the shelf
 * started carrying all four media those cards drew their titles as text while their artwork sat
 * unread on the row. Nothing failed; the cards just looked like rows with no art.
 */
class RowArtSlotsTest {

    /** One row per art slot, each carrying that slot and nothing else. */
    private val bySlot = mapOf(
        "boxArtUri" to XMBItem(id = "1", title = "t", boxArtUri = "file:///box.png"),
        "coverUri" to XMBItem(id = "2", title = "t", coverUri = "file:///cover.png"),
        "artworkUri" to XMBItem(id = "3", title = "t", artworkUri = "file:///art.png"),
        "heroUri" to XMBItem(id = "4", title = "t", heroUri = "file:///hero.png"),
        "iconUri" to XMBItem(id = "5", title = "t", iconUri = "file:///icon.png"),
    )

    @Test
    fun `every art slot is seen by both the backdrop list and the shelf card`() {
        val missedByCard = bySlot.filterValues { it.shelfCoverArt == null }.keys
        val missedByBackdrop = bySlot.filterValues { it.backdropArt.isEmpty() }.keys

        assertEquals(
            "these slots fill a row's art but the home shelf card cannot see them, so the card " +
                "falls back to drawing the title as text: $missedByCard",
            emptySet<String>(),
            missedByCard,
        )
        assertEquals(emptySet<String>(), missedByBackdrop)
    }

    @Test
    fun `the card prefers a portrait cover and the backdrop prefers a landscape`() {
        // The orders differ on purpose; this pins the difference so "make them the same list"
        // is a decision someone has to take deliberately rather than a tidy-up.
        val all = XMBItem(
            id = "6", title = "t",
            boxArtUri = "file:///box.png", coverUri = "file:///cover.png",
            artworkUri = "file:///art.png", heroUri = "file:///hero.png",
        )

        assertEquals("file:///box.png", all.shelfCoverArt)
        assertEquals("file:///art.png", all.backdropArt.first())
    }

    @Test
    fun `a row with no art at all has none, rather than an empty string`() {
        // The card checks for null to decide whether to draw the title instead. A blank string
        // would send an empty model to the image loader and draw nothing at all.
        val bare = XMBItem(id = "7", title = "t")

        assertNull(bare.shelfCoverArt)
        assertEquals(emptyList<String>(), bare.backdropArt)
        assertNull(XMBItem(id = "8", title = "t", coverUri = "  ").shelfCoverArt)
        assertNotNull(XMBItem(id = "9", title = "t", coverUri = "file:///c.png").shelfCoverArt)
    }
}
