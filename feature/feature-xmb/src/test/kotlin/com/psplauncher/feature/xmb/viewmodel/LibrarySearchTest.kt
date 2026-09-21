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

    @Test
    fun `an empty library says so instead of claiming nothing matched`() {
        // The same argument as LOADING, one step further out, and it was missing. On a device with
        // no games, typing three letters answered "No matches — nothing here matches that": a
        // statement about a library that has never held anything, indistinguishable on screen from
        // a real empty result. The user goes looking in Library Manager for a file they never added.
        assertEquals(
            SearchEmptyState.EMPTY_LIBRARY,
            searchEmptyState(loaded = true, query = "zel", anyContent = false),
        )
    }

    @Test
    fun `an empty library says so before it invites a search`() {
        // Ordering, like LOADING above. With nothing to find, "Type to search" is an invitation
        // that cannot succeed, so the emptier fact wins.
        assertEquals(
            SearchEmptyState.EMPTY_LIBRARY,
            searchEmptyState(loaded = true, query = "", anyContent = false),
        )
    }

    @Test
    fun `still loading beats an empty library`() {
        // A library that has not been read yet LOOKS empty. Saying "no games yet" before the read
        // finishes is the original bug wearing a new message.
        assertEquals(
            SearchEmptyState.LOADING,
            searchEmptyState(loaded = false, query = "zel", anyContent = false),
        )
    }

    @Test
    fun `a library with content still distinguishes prompt from no matches`() {
        // The guard on the guard: the new branch must not swallow the two it sits above.
        assertEquals(SearchEmptyState.PROMPT, searchEmptyState(loaded = true, query = "", anyContent = true))
        assertEquals(SearchEmptyState.NO_MATCHES, searchEmptyState(loaded = true, query = "zel", anyContent = true))
    }

    @Test
    fun `every scope can say what to do about being empty`() {
        // The message names the screen that fixes it, and it differs per library — a Video search
        // must not send the user to the ROM roots. A blank one would render an empty second line.
        SearchScope.entries.forEach { scope ->
            assertTrue("blank emptyTitle for $scope", scope.emptyTitle.isNotBlank())
            assertTrue("blank emptyHint for $scope", scope.emptyHint.isNotBlank())
            assertTrue("$scope does not say where to go", scope.emptyHint.length > 10)
        }
    }
}
