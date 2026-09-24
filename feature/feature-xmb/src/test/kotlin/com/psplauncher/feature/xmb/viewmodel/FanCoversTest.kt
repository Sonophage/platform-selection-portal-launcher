package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins which covers reach the fan on the right of the crossbar.
 *
 * The interesting failure is not "does it return three". It is the ORDER of sort, map and take:
 * every wrong arrangement still returns a plausible list of covers, and the one that matters only
 * shows up on a card whose newest games happen to be unscraped — which is most cards, right after
 * a scan and before an artwork run.
 */
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
        // THE test. The three newest unscraped. Taking before mapping hands back an empty list
        // while four covers sit right behind it — and an empty list is indistinguishable from a
        // card that genuinely has no art, so nothing downstream can tell it went wrong.
        val games = listOf(
            game(10), game(9), game(8),
            game(7, "seven"), game(6, "six"), game(5, "five"), game(4, "four"),
        )
        assertEquals(listOf("seven", "six", "five", "four"), fanCoversOf(games))
    }

    @Test
    fun `box art wins over the generic artwork path`() {
        // Both are portrait-ish, but boxArtUri is the 2D box front and artworkUri is whatever the
        // cache happened to keep. A fan of mixed sources reads as mixed sources.
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
        // One list, two readers: the fan takes three and the card's art grid takes four, so the
        // default limit is the LARGER. Computing it at three would have silently starved the grid
        // of its fourth cover — a quadrant that is empty on every card, everywhere, which reads
        // as a library with nothing in it rather than as a number being wrong.
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
}

/**
 * Pins the offset that stops a media column repeating itself.
 *
 * A column's rows are cuts of ONE library — Songs, Artists, Albums, Playlists — so they draw from
 * one pool and are told apart by where their window starts. Without the offset every row shows the
 * same four covers while claiming to stand for something different, which looks deliberate and is
 * the whole reason this is not just `pool.take(4)` at each row.
 */
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
        // Past the end this is EMPTY, and an empty list draws no grid. Wrapping would put the
        // first row's covers on the last row, which reads as a repeat rather than as an end —
        // and the row keeps the glyph it always had, which is a better answer than a wrong one.
        assertEquals(listOf("c13", "c14"), pool.gridSliceAt(3))
        assertTrue(pool.gridSliceAt(4).isEmpty())
        assertTrue(pool.gridSliceAt(99).isEmpty())
    }

    @Test
    fun `the pool is big enough for a real column`() {
        // Music is the longest root: Now Playing, Songs, Artists, Albums, Playlists, plus the
        // installed music apps. A pool short of that would leave the bottom rows bare while the
        // top ones had art, which looks like missing data rather than a cap.
        assertTrue(
            "the pool must cover at least five rows",
            MEDIA_COVER_POOL >= GRID_COVER_COUNT * 5,
        )
    }
}
