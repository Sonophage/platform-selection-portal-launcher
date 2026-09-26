package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentsShelfTest {
    private fun row(at: Long, title: String) = at to XMBItem(id = title, title = title)

    private val games = listOf(row(500, "Skyrim"), row(100, "Crash"))
    private val music = listOf(row(400, "Aja"))
    private val books = listOf(row(300, "Dune"))
    private val videos = listOf(row(200, "Akira"))

    private val noApps = emptyList<Pair<Long, XMBItem>>()

    @Test
    fun `All interleaves the four media by recency, not by medium`() {
        val merged = mergeRecents(games, music, books, videos, noApps, RecentFilter.ALL, limit = 10)

        assertEquals(listOf("Skyrim", "Aja", "Dune", "Akira", "Crash"), merged.map { it.title })
    }

    @Test
    fun `a filter shows that medium's newest, not the survivors of a cut made across all four`() {
        val merged = mergeRecents(games, music, books, videos, noApps, RecentFilter.GAMES, limit = 2)

        assertEquals(listOf("Skyrim", "Crash"), merged.map { it.title })
    }

    @Test
    fun `a medium with nothing in it yields an empty shelf rather than everything`() {
        assertEquals(
            emptyList<String>(),
            mergeRecents(games, music, emptyList(), videos, noApps, RecentFilter.BOOKS, 10).map { it.title },
        )
    }

    @Test
    fun `the cycle visits every filter once and returns to All`() {
        val seen = generateSequence(RecentFilter.ALL) { it.next(includeApps = false) }
            .drop(1)
            .take(4)
            .toList()

        assertEquals(
            listOf(
                RecentFilter.GAMES, RecentFilter.MUSIC, RecentFilter.BOOKS,
                RecentFilter.VIDEO,
            ),
            seen,
        )
    }

    @Test
    fun `Apps is not in the cycle while it is switched off`() {
        assertEquals(RecentFilter.ALL, RecentFilter.VIDEO.next(includeApps = false))
        assertEquals(RecentFilter.APPS, RecentFilter.VIDEO.next(includeApps = true))
        assertEquals(RecentFilter.ALL, RecentFilter.APPS.next(includeApps = true))
    }

    @Test
    fun `a filter that has just been switched off falls back to All`() {
        assertEquals(RecentFilter.ALL, RecentFilter.APPS.next(includeApps = false))
    }

    @Test
    fun `apps are merged by recency like any other medium, and only when present`() {
        val apps = listOf(row(250, "Termux"))

        assertEquals(
            listOf("Skyrim", "Aja", "Dune", "Termux", "Akira", "Crash"),
            mergeRecents(games, music, books, videos, apps, RecentFilter.ALL, limit = 10).map { it.title },
        )

        assertEquals(
            emptyList<String>(),
            mergeRecents(games, music, books, videos, noApps, RecentFilter.APPS, 10).map { it.title },
        )
    }

    @Test
    fun `the limit is honoured across the merge`() {
        val merged = mergeRecents(games, music, books, videos, noApps, RecentFilter.ALL, limit = 3)

        assertEquals(listOf("Skyrim", "Aja", "Dune"), merged.map { it.title })
    }
}
