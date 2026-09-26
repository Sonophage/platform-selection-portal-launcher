package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RowArtSlotsTest {
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
        val bare = XMBItem(id = "7", title = "t")

        assertNull(bare.shelfCoverArt)
        assertEquals(emptyList<String>(), bare.backdropArt)
        assertNull(XMBItem(id = "8", title = "t", coverUri = "  ").shelfCoverArt)
        assertNotNull(XMBItem(id = "9", title = "t", coverUri = "file:///c.png").shelfCoverArt)
    }
}
