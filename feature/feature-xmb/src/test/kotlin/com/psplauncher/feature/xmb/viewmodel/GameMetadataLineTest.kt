package com.psplauncher.feature.xmb.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

        assertNull(gameMetadataLine(null, "", "   ", ""))
    }

    @Test
    fun `a missing field is dropped rather than printed as a gap`() {
        assertEquals("2009   ·   Nihon Falcom", gameMetadataLine(2009, null, "Nihon Falcom", null))
        assertEquals("Puzzle", gameMetadataLine(null, "Puzzle", null, null))

        assertEquals(false, gameMetadataLine(null, "Puzzle", null, null)!!.contains("·"))
    }

    @Test
    fun `year zero is the scraper's unknown, not a year`() {
        assertEquals("Konami", gameMetadataLine(0, null, "Konami", null))
        assertNull(gameMetadataLine(0, null, null, null))
    }

    @Test
    fun `the player count carries its noun and is singular for one`() {
        assertEquals("1 player", gameMetadataLine(null, null, null, "1"))
        assertEquals("1-5 players", gameMetadataLine(null, null, null, "1-5"))
        assertEquals("2 players", gameMetadataLine(null, null, null, "2"))
    }

    @Test
    fun `surrounding whitespace never becomes a part`() {
        assertEquals("Konami", gameMetadataLine(null, "  ", " Konami ", null))
    }
}
