package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The A–Z rail's rules.
 *
 * The one that matters is [a list that is not alphabetical gets no rail][`a list sorted by
 * something other than title gets no rail at all`]. Everything else here is arithmetic; that one
 * is the feature's honesty. A rail drawn over a date-sorted list still looks right — 26 rungs,
 * a cursor, a smooth drag — and every jump lands on the wrong row, which nobody reports as a bug
 * because it reads as "the scroll position is a bit off". Refusing to draw it is the whole point
 * of deriving the rungs from the list instead of declaring which sorts are allowed.
 */
class LetterJumpTest {

    private fun item(title: String) = XMBItem(id = title, title = title)

    /** [n] rows starting at 'A', wrapping the alphabet — long enough to clear the minimum. */
    private fun alphabetical(n: Int = 40): List<XMBItem> =
        (0 until n).map { item("${('A' + it % 26)}$it title") }.sortedBy { it.title.lowercase() }

    @Test
    fun `an alphabetical list gets one rung per distinct initial`() {
        val anchors = letterAnchors(alphabetical(40))
        assertNotNull("40 alphabetical rows should raise a rail", anchors)
        // 40 rows over a 26-letter cycle: every letter appears at least once.
        assertEquals("one rung per distinct initial", 26, anchors!!.size)
        assertEquals("the rail starts at A", 'A', anchors.first().letter)
        assertEquals("A's rung points at the first row", 0, anchors.first().index)
    }

    @Test
    fun `a list sorted by something other than title gets no rail at all`() {
        // Exactly the shape of a Date Added or Recently Played column: plenty of rows, plenty of
        // distinct initials, and the initials do not run in order.
        val byDate = listOf("Zelda", "Astro Bot", "Metroid", "Barnyard", "Yakuza")
            .flatMap { base -> (0 until 8).map { item("$base $it") } }
        assertTrue("fixture must clear the length minimum", byDate.size >= LETTER_JUMP_MIN_ITEMS)
        assertNull(
            "a rail over a non-alphabetical list would point at the wrong rows, so there must be none",
            letterAnchors(byDate),
        )
    }

    @Test
    fun `a short list gets no rail`() {
        val short = (0 until LETTER_JUMP_MIN_ITEMS - 1).map { item("${('A' + it)}") }
        assertNull("below the minimum the rail is not worth the screen", letterAnchors(short))
    }

    @Test
    fun `a long list with too few letters gets no rail`() {
        // 30 rows, all under two initials — alphabetical, long enough, and still not a scrubber.
        val twoLetters = (0 until 15).map { item("Alpha $it") } + (0 until 15).map { item("Beta $it") }
        assertNull("two rungs is a toggle, not a scrubber", letterAnchors(twoLetters))
    }

    @Test
    fun `digits and punctuation file under hash, and hash leads`() {
        val mixed = (0 until 10).map { item("$it Racer") } +
            (0 until 10).map { item("Alpha $it") } +
            (0 until 10).map { item("Beta $it") }
        val anchors = letterAnchors(mixed.sortedBy { it.title.lowercase() })
        assertNotNull(anchors)
        assertEquals("non-letter initials lead, as they do in the sort", '#', anchors!!.first().letter)
        assertEquals("three rungs: # A B", 3, anchors.size)
    }

    @Test
    fun `an accented initial keeps its own rung instead of breaking the rail`() {
        // 'é' sorts after 'z' under lowercase(), and 'É' sorts after 'Z' here — the two orders
        // agree, so the rail survives and simply grows a rung. Folding it into '#' would file it
        // at the front while the row sits at the back, and the whole rail would be withheld.
        val withAccent = (0 until 12).map { item("Alpha $it") } +
            (0 until 12).map { item("Beta $it") } +
            (0 until 12).map { item("Étude $it") }
        val anchors = letterAnchors(withAccent.sortedBy { it.title.lowercase() })
        assertNotNull("one accented title must not withhold the whole rail", anchors)
        assertEquals("A B É", 3, anchors!!.size)
        assertEquals('É', anchors.last().letter)
    }

    @Test
    fun `the rail opens on the rung the cursor is already in`() {
        val items = alphabetical(40)
        val anchors = letterAnchors(items)!!
        val cRung = anchors.indexOfFirst { it.letter == 'C' }
        // A row PARTWAY THROUGH C, deliberately not C's first row, and that is the whole fixture.
        // Handed C's anchor exactly, returnIndex and targetIndex are the same number and the
        // second assertion below compares a value to itself — it passes under any implementation,
        // including one that dropped returnIndex entirely. It did, until this line moved.
        val partwayThroughC = anchors[cRung].index + 1
        val state = letterJumpFor(items, currentIndex = partwayThroughC)
        assertNotNull(state)
        assertEquals("opens where the eye already is, not at A", 'C', state!!.letter)
        assertEquals(
            "remembers where the cursor actually was, which is not where the rung points",
            partwayThroughC,
            state.returnIndex,
        )
        assertNotEquals(
            "fixture is only honest while those two differ",
            state.returnIndex,
            state.targetIndex,
        )
    }

    @Test
    fun `moving the rail stops at both ends instead of wrapping`() {
        val state = letterJumpFor(alphabetical(40), currentIndex = 0)!!
        assertEquals("already at the top, up is a no-op", state, state.move(-1))
        val end = state.move(1000)
        assertEquals("walks to the last rung and stops", state.anchors.lastIndex, end.cursor)
        assertEquals("already at the end, down is a no-op", end, end.move(1))
    }

    @Test
    fun `a finger at each end of the rail lands on each end rung`() {
        val state = letterJumpFor(alphabetical(40), currentIndex = 20)!!
        assertEquals("top of the rail is the first rung", 0, state.atFraction(0f).cursor)
        assertEquals("bottom of the rail is the last", state.anchors.lastIndex, state.atFraction(1f).cursor)
        assertEquals("out of range clamps rather than throwing", 0, state.atFraction(-5f).cursor)
    }

    @Test
    fun `the target index is the first row of the chosen letter`() {
        val items = alphabetical(40)
        val state = letterJumpFor(items, currentIndex = 0)!!.move(2)
        val landed = items[state.targetIndex]
        assertEquals(
            "the row under the cursor starts with the letter the rung shows",
            state.letter,
            initialOf(landed.title),
        )
    }
}
