package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The home shelf merges four libraries that each keep their own recency column, so the ordering
 * is the whole feature: get it wrong and the shelf still renders, still looks like a shelf, and
 * quietly tells the user they were doing something other than what they were doing.
 */
class RecentsShelfTest {

    private fun row(at: Long, title: String) = at to XMBItem(id = title, title = title)

    private val games = listOf(row(500, "Skyrim"), row(100, "Crash"))
    private val music = listOf(row(400, "Aja"))
    private val books = listOf(row(300, "Dune"))
    private val videos = listOf(row(200, "Akira"))

    @Test
    fun `All interleaves the four media by recency, not by medium`() {
        // The point of the feature. A merge that concatenated and then sorted within each medium
        // would produce a plausible-looking list that is simply not "what I did last".
        val merged = mergeRecents(games, music, books, videos, RecentFilter.ALL, limit = 10)

        assertEquals(listOf("Skyrim", "Aja", "Dune", "Akira", "Crash"), merged.map { it.title })
    }

    @Test
    fun `a filter shows that medium's newest, not the survivors of a cut made across all four`() {
        // limit AFTER filter. Applied the other way round, asking for Games with a limit of 2 on
        // this data would return Skyrim alone — Crash having been cut by the four-way trim — and
        // the shelf would claim the user has played one game.
        val merged = mergeRecents(games, music, books, videos, RecentFilter.GAMES, limit = 2)

        assertEquals(listOf("Skyrim", "Crash"), merged.map { it.title })
    }

    @Test
    fun `a medium with nothing in it yields an empty shelf rather than everything`() {
        // The empty-state path. Returning all four here would be the classic filter bug: the user
        // asks for Books, has read none, and is shown their games.
        assertEquals(
            emptyList<String>(),
            mergeRecents(games, music, emptyList(), videos, RecentFilter.BOOKS, 10).map { it.title },
        )
    }

    @Test
    fun `the cycle visits every filter once and returns to All`() {
        // X wraps. If next() ever skipped a medium, that medium would be unreachable by the only
        // control that selects it.
        val seen = generateSequence(RecentFilter.ALL) { it.next() }
            .drop(1)
            .take(RecentFilter.entries.size)
            .toList()

        assertEquals(
            listOf(
                RecentFilter.GAMES, RecentFilter.MUSIC, RecentFilter.BOOKS,
                RecentFilter.VIDEO, RecentFilter.ALL,
            ),
            seen,
        )
    }

    @Test
    fun `the limit is honoured across the merge`() {
        val merged = mergeRecents(games, music, books, videos, RecentFilter.ALL, limit = 3)

        assertEquals(listOf("Skyrim", "Aja", "Dune"), merged.map { it.title })
    }
}
