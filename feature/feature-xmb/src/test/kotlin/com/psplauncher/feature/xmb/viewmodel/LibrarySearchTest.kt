package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertTrue(matchesSearch("tolkien hobbit", "The Hobbit", "J.R.R. Tolkien"))
        assertFalse(matchesSearch("tolkien dune", "The Hobbit", "J.R.R. Tolkien"))

        assertTrue(matchesSearch("hobbit", "The Hobbit", null))
        assertFalse(matchesSearch("tolkien", "The Hobbit", null))
    }

    @Test
    fun `a blank query matches nothing at all`() {
        assertFalse(matchesSearch("", "The Hobbit"))
        assertFalse(matchesSearch("   ", "The Hobbit"))
        assertFalse(matchesSearch("!!!", "The Hobbit"))
    }

    @Test
    fun `an entry with nothing to match on is never a hit`() {
        assertFalse(matchesSearch("anything", ""))
        assertFalse(matchesSearch("anything", null, null))
    }

    @Test
    fun `a partial word still matches`() {
        assertTrue(matchesSearch("ocar", "The Legend of Zelda: Ocarina of Time"))
        assertTrue(matchesSearch("fina fan", "Final Fantasy VII"))
    }

    @Test
    fun `an empty list says which kind of empty it is`() {
        assertEquals(SearchEmptyState.LOADING, searchEmptyState(loaded = false, query = ""))
        assertEquals(SearchEmptyState.LOADING, searchEmptyState(loaded = false, query = "zelda"))

        assertEquals(SearchEmptyState.PROMPT, searchEmptyState(loaded = true, query = ""))

        assertEquals(SearchEmptyState.PROMPT, searchEmptyState(loaded = true, query = "  !!  "))

        assertEquals(SearchEmptyState.NO_MATCHES, searchEmptyState(loaded = true, query = "zelda"))
    }

    @Test
    fun `an empty library says so instead of claiming nothing matched`() {
        assertEquals(
            SearchEmptyState.EMPTY_LIBRARY,
            searchEmptyState(loaded = true, query = "zel", anyContent = false),
        )
    }

    @Test
    fun `an empty library says so before it invites a search`() {
        assertEquals(
            SearchEmptyState.EMPTY_LIBRARY,
            searchEmptyState(loaded = true, query = "", anyContent = false),
        )
    }

    @Test
    fun `still loading beats an empty library`() {
        assertEquals(
            SearchEmptyState.LOADING,
            searchEmptyState(loaded = false, query = "zel", anyContent = false),
        )
    }

    @Test
    fun `a library with content still distinguishes prompt from no matches`() {
        assertEquals(SearchEmptyState.PROMPT, searchEmptyState(loaded = true, query = "", anyContent = true))
        assertEquals(SearchEmptyState.NO_MATCHES, searchEmptyState(loaded = true, query = "zel", anyContent = true))
    }

    @Test
    fun `every scope can say what to do about being empty`() {
        SearchScope.entries.forEach { scope ->
            assertTrue("blank emptyTitle for $scope", scope.emptyTitle.isNotBlank())
            assertTrue("blank emptyHint for $scope", scope.emptyHint.isNotBlank())
            assertTrue("$scope does not say where to go", scope.emptyHint.length > 10)
        }
    }
}
