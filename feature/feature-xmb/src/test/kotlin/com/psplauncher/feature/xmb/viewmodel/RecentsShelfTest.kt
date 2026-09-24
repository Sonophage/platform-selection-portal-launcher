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

    /** What the caller hands over when the setting is off — see mergeRecents' `apps`. */
    private val noApps = emptyList<Pair<Long, XMBItem>>()

    @Test
    fun `All interleaves the four media by recency, not by medium`() {
        // The point of the feature. A merge that concatenated and then sorted within each medium
        // would produce a plausible-looking list that is simply not "what I did last".
        val merged = mergeRecents(games, music, books, videos, noApps, RecentFilter.ALL, limit = 10)

        assertEquals(listOf("Skyrim", "Aja", "Dune", "Akira", "Crash"), merged.map { it.title })
    }

    @Test
    fun `a filter shows that medium's newest, not the survivors of a cut made across all four`() {
        // limit AFTER filter. Applied the other way round, asking for Games with a limit of 2 on
        // this data would return Skyrim alone — Crash having been cut by the four-way trim — and
        // the shelf would claim the user has played one game.
        val merged = mergeRecents(games, music, books, videos, noApps, RecentFilter.GAMES, limit = 2)

        assertEquals(listOf("Skyrim", "Crash"), merged.map { it.title })
    }

    @Test
    fun `a medium with nothing in it yields an empty shelf rather than everything`() {
        // The empty-state path. Returning all four here would be the classic filter bug: the user
        // asks for Books, has read none, and is shown their games.
        assertEquals(
            emptyList<String>(),
            mergeRecents(games, music, emptyList(), videos, noApps, RecentFilter.BOOKS, 10).map { it.title },
        )
    }

    @Test
    fun `the cycle visits every filter once and returns to All`() {
        // X wraps. If next() ever skipped a medium, that medium would be unreachable by the only
        // control that selects it.
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

    /**
     * Apps is in the enum whether or not it is switched on, and the cycle has to skip it.
     *
     * The alternative — walking `entries` by ordinal — would land the X button on a filter the
     * shelf can never fill, and the row of names a finger taps walked exactly that way until this
     * existed. One list, read by both, is RecentFilter.visible.
     */
    @Test
    fun `Apps is not in the cycle while it is switched off`() {
        assertEquals(RecentFilter.ALL, RecentFilter.VIDEO.next(includeApps = false))
        assertEquals(RecentFilter.APPS, RecentFilter.VIDEO.next(includeApps = true))
        assertEquals(RecentFilter.ALL, RecentFilter.APPS.next(includeApps = true))
    }

    /**
     * Switched off underneath the cursor.
     *
     * Someone standing on Apps who turns the setting off is on a filter that no longer exists.
     * Walking from there by ordinal would step to whatever follows it in the enum; falling back
     * to All is the only answer that is always a real filter.
     */
    @Test
    fun `a filter that has just been switched off falls back to All`() {
        assertEquals(RecentFilter.ALL, RecentFilter.APPS.next(includeApps = false))
    }

    @Test
    fun `apps are merged by recency like any other medium, and only when present`() {
        val apps = listOf(row(250, "Termux"))

        // In ALL they interleave by stamp rather than being appended after the media.
        assertEquals(
            listOf("Skyrim", "Aja", "Dune", "Termux", "Akira", "Crash"),
            mergeRecents(games, music, books, videos, apps, RecentFilter.ALL, limit = 10).map { it.title },
        )
        // And with the setting off the caller hands over an empty list, so ALL and APPS agree.
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
