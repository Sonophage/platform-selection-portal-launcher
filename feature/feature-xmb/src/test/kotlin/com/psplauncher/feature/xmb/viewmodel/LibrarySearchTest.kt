package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a library search finds.
 *
 * A search rule fails silently in the worst direction: too strict and it returns nothing for a
 * query the user is certain should work, with no way to tell whether the thing is missing or the
 * search is. So the cases pinned here are the ones a real library actually contains -- colons,
 * hyphens, underscores, roman numerals, words in the wrong order -- rather than a tidy set of
 * lowercase words.
 */
class LibrarySearchTest {

    @Test
    fun `punctuation on either side is ignored`() {
        assertEquals("the legend of zelda ocarina of time", normalizeForSearch("The Legend of Zelda: Ocarina of Time"))
        assertEquals("spider man", normalizeForSearch("Spider-Man"))
        assertEquals("the legend mp4", normalizeForSearch("THE_LEGEND.mp4"))
        assertEquals("", normalizeForSearch("   "))
        assertEquals("", normalizeForSearch("---"))
    }

    @Test
    fun `every word must match, in any order`() {
        val title = "The Legend of Zelda: Ocarina of Time"
        assertTrue(matchesSearch("zelda", title))
        assertTrue("word order must not matter", matchesSearch("ocarina zelda", title))
        assertTrue("punctuation in the query is ignored too", matchesSearch("zelda: ocarina", title))
        assertFalse("a word that is not there fails the whole query", matchesSearch("zelda majora", title))
    }

    @Test
    fun `a match can span several fields`() {
        // A book search types an author and a title word together and expects both to count.
        assertTrue(matchesSearch("tolkien hobbit", "The Hobbit", "J.R.R. Tolkien"))
        assertFalse(matchesSearch("tolkien dune", "The Hobbit", "J.R.R. Tolkien"))
        // A null field is simply absent, not a match and not a crash.
        assertTrue(matchesSearch("hobbit", "The Hobbit", null))
        assertFalse(matchesSearch("tolkien", "The Hobbit", null))
    }

    @Test
    fun `a blank query matches nothing at all`() {
        // Load-bearing: the search screen shows a "type to search" hint on an empty box instead of
        // listing the entire library, and it can only tell the difference because of this.
        assertFalse(matchesSearch("", "The Hobbit"))
        assertFalse(matchesSearch("   ", "The Hobbit"))
        assertFalse(matchesSearch("!!!", "The Hobbit"))
    }

    @Test
    fun `an entry with nothing to match on is never a hit`() {
        // A property rather than a guard: it falls out of the all-terms rule, because an empty
        // string contains no non-empty term. Worth stating anyway -- a library does contain rows
        // with no title, and "they never match" is the behaviour, not an accident.
        assertFalse(matchesSearch("anything", ""))
        assertFalse(matchesSearch("anything", null, null))
    }

    @Test
    fun `a partial word still matches`() {
        // Typing is expensive on a handheld. "ocar" should be enough.
        assertTrue(matchesSearch("ocar", "The Legend of Zelda: Ocarina of Time"))
        assertTrue(matchesSearch("fina fan", "Final Fantasy VII"))
    }

    @Test
    fun `an empty list says which kind of empty it is`() {
        // Loading wins over everything, including a blank query: all three conditions are true
        // the instant a search opens, and only one of the three answers is honest.
        assertEquals(SearchEmptyState.LOADING, searchEmptyState(loaded = false, query = ""))
        assertEquals(SearchEmptyState.LOADING, searchEmptyState(loaded = false, query = "zelda"))

        assertEquals(SearchEmptyState.PROMPT, searchEmptyState(loaded = true, query = ""))
        // Punctuation only is still nothing to search for, and must not claim no matches.
        assertEquals(SearchEmptyState.PROMPT, searchEmptyState(loaded = true, query = "  !!  "))

        assertEquals(SearchEmptyState.NO_MATCHES, searchEmptyState(loaded = true, query = "zelda"))
    }
}
