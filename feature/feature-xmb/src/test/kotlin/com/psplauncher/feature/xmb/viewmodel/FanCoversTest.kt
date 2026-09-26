package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FanCoversTest {
    private fun game(id: Long, box: String? = null, art: String? = null) =
        Game(id = id, platformId = "gba", title = "g$id", romPath = "/r/$id", boxArtUri = box, artworkUri = art)

    @Test
    fun `newest first, by id`() {
        val covers = fanCoversOf(
            listOf(game(1, "one"), game(9, "nine"), game(5, "five")),
        )
        assertEquals(listOf("nine", "five", "one"), covers)
    }

    @Test
    fun `the newest that HAVE art, not the art among the newest`() {
        val games = listOf(
            game(10), game(9), game(8),
            game(7, "seven"), game(6, "six"), game(5, "five"), game(4, "four"),
        )
        assertEquals(listOf("seven", "six", "five", "four"), fanCoversOf(games))
    }

    @Test
    fun `box art wins over the generic artwork path`() {
        assertEquals(
            listOf("box"),
            fanCoversOf(listOf(game(1, box = "box", art = "art"))),
        )
        assertEquals(
            listOf("art"),
            fanCoversOf(listOf(game(1, box = null, art = "art"))),
        )
    }

    @Test
    fun `fewer covers than slots is a shorter fan, not a padded one`() {
        assertEquals(listOf("a"), fanCoversOf(listOf(game(1, "a"))))
        assertTrue(fanCoversOf(emptyList()).isEmpty())
        assertTrue("a card whose games are all unscraped has no fan", fanCoversOf(listOf(game(1), game(2))).isEmpty())
    }

    @Test
    fun `the default carries enough for the hungriest consumer`() {
        val many = (1..50L).map { game(it, "c$it") }
        assertEquals(INSIDE_COVER_COUNT, fanCoversOf(many).size)
        assertEquals(listOf("c50", "c49", "c48", "c47"), fanCoversOf(many))
        assertTrue(
            "the shared list must cover both readers",
            INSIDE_COVER_COUNT >= FAN_COVER_COUNT && INSIDE_COVER_COUNT >= GRID_COVER_COUNT,
        )
    }

    @Test
    fun `an explicit limit still wins`() {
        val many = (1..50L).map { game(it, "c$it") }
        assertEquals(listOf("c50", "c49", "c48"), fanCoversOf(many, FAN_COVER_COUNT))
    }

    @Test
    fun `the fan draws nothing when card art grid is off`() {
        val covers = listOf("a.png", "b.png", "c.png", "d.png")
        assertEquals(
            "Card Art Grid off means the card shows its console icon — a fan of the covers from " +
                "inside it, drawn beside that icon, is the setting obeyed by one consumer and " +
                "ignored by the other",
            emptyList<String>(),
            fanCoversToDraw(covers, cardArtGrid = false),
        )
    }

    @Test
    fun `the fan takes its own count when card art grid is on`() {
        val covers = listOf("a.png", "b.png", "c.png", "d.png")

        assertEquals(
            "the fan takes FAN_COVER_COUNT, not everything the row carries for the grid",
            listOf("a.png", "b.png", "c.png"),
            fanCoversToDraw(covers, cardArtGrid = true),
        )
    }

    @Test
    fun `a card with nothing inside draws no fan either way`() {
        assertEquals(emptyList<String>(), fanCoversToDraw(emptyList(), cardArtGrid = true))
        assertEquals(emptyList<String>(), fanCoversToDraw(emptyList(), cardArtGrid = false))
    }
}

class GridSliceTest {
    private val pool = (1..14).map { "c$it" }

    @Test
    fun `each row gets a different window`() {
        assertEquals(listOf("c1", "c2", "c3", "c4"), pool.gridSliceAt(0))
        assertEquals(listOf("c5", "c6", "c7", "c8"), pool.gridSliceAt(1))
        assertEquals(listOf("c9", "c10", "c11", "c12"), pool.gridSliceAt(2))
    }

    @Test
    fun `no cover appears on two rows`() {
        val seen = (0..2).flatMap { pool.gridSliceAt(it) }
        assertEquals("the windows must not overlap", seen.size, seen.toSet().size)
    }

    @Test
    fun `a short pool runs out rather than wrapping`() {
        assertEquals(listOf("c13", "c14"), pool.gridSliceAt(3))
        assertTrue(pool.gridSliceAt(4).isEmpty())
        assertTrue(pool.gridSliceAt(99).isEmpty())
    }

    @Test
    fun `the pool is big enough for a real column`() {
        assertTrue(
            "the pool must cover at least five rows",
            MEDIA_COVER_POOL >= GRID_COVER_COUNT * 5,
        )
    }
}
