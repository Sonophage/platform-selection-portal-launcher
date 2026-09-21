package com.psplauncher.feature.xmb.ui.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Artwork Studio's status band.
 *
 * Every number here is a value against a limit, and each has a way of being quietly wrong: a zero
 * denominator, a count that has passed its own cap, a cap nobody has told us yet, and — the one
 * that actually misleads — a source that has not answered showing the same "0" as a source that
 * answered with nothing.
 */
class StudioStatusBandTest {

    private fun stats(
        filled: Int = 5, total: Int = 11, found: Int = 12, loading: Boolean = false,
        today: Int? = 418, cap: Int? = 20_000,
    ) = studioStats(filled, total, found, loading, today, cap)

    @Test
    fun `the three counters read value against limit`() {
        val s = stats()
        assertEquals(listOf("Filled", "Found", "Requests"), s.map { it.label })
        assertEquals("5 / 11", s[0].value)
        assertEquals("12", s[1].value)
        assertEquals("418 / 20000", s[2].value)
    }

    @Test
    fun `a source still loading is a dash, not a zero`() {
        // THE ONE THAT MISLEADS. "0" means this source has nothing for this game, which is a
        // reason to switch source. A source that has not answered yet is not that, and showing
        // the same glyph for both makes the user act on a result that does not exist.
        assertEquals("…", stats(found = 0, loading = true)[1].value)
        assertEquals("0", stats(found = 0, loading = false)[1].value)
    }

    @Test
    fun `an unknown daily cap shows the count alone rather than inventing a denominator`() {
        // Before any authenticated response there is no cap. An unknown limit is not an unlimited
        // one, and a bar drawn against a guess is worse than no bar.
        val s = stats(today = 418, cap = null)
        assertEquals("418", s[2].value)
        assertNull(s[2].fraction)
    }

    @Test
    fun `no quota at all reads as a dash`() {
        val s = stats(today = null, cap = null)
        assertEquals("—", s[2].value)
        assertNull(s[2].fraction)
    }

    @Test
    fun `a zero denominator yields no bar rather than a divide`() {
        assertNull(stats(filled = 0, total = 0)[0].fraction)
        assertNull(stats(today = 5, cap = 0)[2].fraction)
    }

    @Test
    fun `a count past its own cap fills the bar rather than overflowing it`() {
        // ScreenScraper's counter has been seen to exceed the cap it reports. A fraction above 1
        // would draw a bar past its track.
        val s = stats(today = 25_000, cap = 20_000)
        assertEquals(1f, s[2].fraction)
        assertEquals("25000 / 20000", s[2].value)
    }

    @Test
    fun `every fraction that exists is a real proportion`() {
        listOf(
            stats(),
            stats(filled = 0, total = 11),
            stats(filled = 11, total = 11),
            stats(today = 0, cap = 20_000),
        ).forEach { s ->
            s.mapNotNull { it.fraction }.forEach { f ->
                assertTrue(f in 0f..1f, "fraction out of range: $f")
            }
        }
    }

    @Test
    fun `Filled describes the game, not the tab being looked at`() {
        // The tab row already says where you are standing. This counter exists to answer the
        // question the tab row cannot: how much of THIS GAME is done, without walking eleven tabs.
        assertEquals("11 / 11", stats(filled = 11, total = 11)[0].value)
        assertEquals(1f, stats(filled = 11, total = 11)[0].fraction)
    }
}
