package com.psplauncher.feature.xmb.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The XMB game info line. Every field is optional because scraping is partial: on the real
 * library 123 of 147 games carry this metadata and the rest carry none of it, so "some of it is
 * missing" is the normal case and not the edge case. The separator must never be left dangling
 * and an absent field must never print as an empty gap.
 */
class GameMetadataLineTest {

    @Test
    fun `a fully scraped game reads year, genre, developer, players`() {
        assertEquals(
            "2005   ·   Role Playing Game   ·   Konami   ·   1-2 players",
            gameMetadataLine(2005, "Role Playing Game", "Konami", "1-2"),
        )
    }

    @Test
    fun `a game with nothing scraped draws no line at all`() {
        assertNull(gameMetadataLine(null, null, null, null))
        // Blank is what an empty scraper column looks like, and it is not the same as absent
        // until something trims it.
        assertNull(gameMetadataLine(null, "", "   ", ""))
    }

    @Test
    fun `a missing field is dropped rather than printed as a gap`() {
        assertEquals("2009   ·   Nihon Falcom", gameMetadataLine(2009, null, "Nihon Falcom", null))
        assertEquals("Puzzle", gameMetadataLine(null, "Puzzle", null, null))
        // The separator belongs BETWEEN parts: one part means no separator anywhere.
        assertEquals(false, gameMetadataLine(null, "Puzzle", null, null)!!.contains("·"))
    }

    @Test
    fun `year zero is the scraper's unknown, not a year`() {
        assertEquals("Konami", gameMetadataLine(0, null, "Konami", null))
        assertNull(gameMetadataLine(0, null, null, null))
    }

    @Test
    fun `the player count carries its noun and is singular for one`() {
        // "1" alone on screen is meaningless next to a year and a genre.
        assertEquals("1 player", gameMetadataLine(null, null, null, "1"))
        assertEquals("1-5 players", gameMetadataLine(null, null, null, "1-5"))
        assertEquals("2 players", gameMetadataLine(null, null, null, "2"))
    }

    @Test
    fun `surrounding whitespace never becomes a part`() {
        assertEquals("Konami", gameMetadataLine(null, "  ", " Konami ", null))
    }
}
