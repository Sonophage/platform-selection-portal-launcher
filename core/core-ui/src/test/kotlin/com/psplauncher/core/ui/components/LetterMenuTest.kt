package com.psplauncher.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterMenuTest {
    private fun titles(vararg letters: Char, each: Int = 9): List<String> =
        letters.flatMap { letter -> (0 until each).map { "$letter title $it" } }

    @Test
    fun `the strip offers each initial once, in order, however the list arrived`() {
        val shuffled = titles('D', 'A', 'C', 'A', 'B', each = 6)

        assertEquals(
            "the strip is a set of initials, so a repeat is not a second rung",
            listOf('A', 'B', 'C', 'D'),
            letterMenuFor(shuffled),
        )
    }

    @Test
    fun `an unsorted list still gets a strip, because a letter filter does not care about order`() {
        val byDate = titles('Z', 'A', 'M')

        assertTrue(
            "filtering needs no positional anchor, so the jump rail's sort rule must not apply here",
            letterMenuFor(byDate).isNotEmpty(),
        )
    }

    @Test
    fun `a short list gets no strip`() {
        val short = (0 until LETTER_JUMP_MIN_ITEMS - 1).map { "${('A' + it % 26)} title $it" }

        assertEquals("below the minimum the strip is not worth the screen", emptyList<Char>(), letterMenuFor(short))
    }

    @Test
    fun `a long list with too few letters gets no strip`() {
        val twoLetters = titles('A', 'B', each = 13)

        assertTrue(
            "the fixture must clear the length minimum, or this passes for the wrong reason",
            twoLetters.size >= LETTER_JUMP_MIN_ITEMS,
        )
        assertEquals("two rungs is a toggle, not a filter", emptyList<Char>(), letterMenuFor(twoLetters))
    }

    @Test
    fun `digits and punctuation share the hash rung`() {
        val mixed = titles('A', 'B') + (0 until 8).map { "$it Racer" } + listOf("!bang one", "!bang two")
        assertTrue("fixture must clear the length minimum", mixed.size >= LETTER_JUMP_MIN_ITEMS)

        assertEquals("non-letter initials all file under one rung", listOf('#', 'A', 'B'), letterMenuFor(mixed))
    }
}
