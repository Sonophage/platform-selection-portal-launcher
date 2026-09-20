package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The line under every library row's title.
 *
 * These are worth pinning because every failure mode here is silent. A field left out of a builder
 * is metadata that was scanned, stored, and then shown on no screen in the app -- which is exactly
 * what had happened to a music track's album. A blank field joined instead of dropped leaves a
 * dangling separator, and a row that reads "Konami  ·" is a bug that renders perfectly.
 *
 * [relativeDate] is always given an explicit `now` so none of this depends on the day it runs.
 */
class LibraryRowTextTest {

    private val day = 86_400_000L
    private val now = 1_760_000_000_000L   // a fixed instant; only differences matter

    // ── Music ────────────────────────────────────────────────────────────────

    @Test
    fun `a music row names the artist, the album and the running time`() {
        assertEquals(
            "Konami  ·  Greatest Hits  ·  3:42",
            musicRowSubtitle("Konami", "Greatest Hits", 222_000L),
        )
    }

    @Test
    fun `the album is carried, because nothing else in the app shows it`() {
        // The whole reason this builder exists. Dropping album from the join is invisible on a
        // track that has an artist and a duration, so it is asserted on its own.
        assertTrue(musicRowSubtitle(null, "Greatest Hits", null)!!.contains("Greatest Hits"))
    }

    @Test
    fun `a track with nothing scanned has no line at all`() {
        assertNull(musicRowSubtitle(null, null, null))
        assertNull(musicRowSubtitle("", "   ", 0L))
    }

    // ── Video ────────────────────────────────────────────────────────────────

    @Test
    fun `a video row names the running time and when it was last watched`() {
        assertEquals(
            "1:42:07  ·  Watched 3 days ago",
            videoRowSubtitle(6_127_000L, now - 3 * day, now),
        )
    }

    @Test
    fun `an unwatched video says nothing about watching, rather than saying never`() {
        assertEquals("1:42:07", videoRowSubtitle(6_127_000L, null, now))
        assertEquals("1:42:07", videoRowSubtitle(6_127_000L, 0L, now))
    }

    @Test
    fun `the watched date is labelled, so it cannot be read as the date added`() {
        // Beside a running time, a bare "3 days ago" is ambiguous. The label is the fix and it is
        // the kind of thing a later tidy-up removes as redundant.
        assertTrue(videoRowSubtitle(1000L, now - 3 * day, now)!!.contains("Watched"))
    }

    // ── Books ────────────────────────────────────────────────────────────────

    @Test
    fun `a book row names the author, then where it falls in its series`() {
        assertEquals(
            "Frank Herbert  ·  Dune #2",
            bookRowSubtitle("Frank Herbert", "Dune", 2.0),
        )
    }

    @Test
    fun `a whole-numbered series entry drops its decimal, a half keeps it`() {
        // "#2.0" is noise; "#2.5" is the information -- a novella between two books.
        assertTrue(bookRowSubtitle(null, "Dune", 2.0)!!.endsWith("#2"))
        assertTrue(bookRowSubtitle(null, "Dune", 2.5)!!.endsWith("#2.5"))
    }

    @Test
    fun `a standalone book is just its author, with no empty series marker`() {
        assertEquals("Ursula K Le Guin", bookRowSubtitle("Ursula K Le Guin", null, null))
        // A blank <meta content=""> in the EPUB must not become a series called nothing.
        assertEquals("Ursula K Le Guin", bookRowSubtitle("Ursula K Le Guin", "   ", 1.0))
    }

    // ── Photos ───────────────────────────────────────────────────────────────

    @Test
    fun `a photo row names the date, the resolution and the file size`() {
        val line = photoRowSubtitle(now, "4032×3024", 3_355_443L)!!
        assertTrue(line, line.contains("4032×3024"))
        assertTrue(line, line.contains("3.2 MB"))
        // Date first: it is what you scan a photo list by.
        assertTrue(line, line.indexOf("4032") > 0 && line.startsWith(formatDate(now)))
    }

    @Test
    fun `a zero size is omitted rather than drawn as a dash`() {
        // The row drops a zero size before formatting rather than printing it. formatByteSize
        // answers "0 B", which is correct for a labelled info row and wrong inside a joined
        // line, where it reads as a fact that exists and happens to be zero.
        //
        // This used to say the formatter answered "—". It did, in a branch nothing could reach:
        // this caller guards zero itself, and it was the only caller. The dash went with the
        // four-way merge into core-common.
        assertEquals("4032×3024", photoRowSubtitle(null, "4032×3024", 0L))
    }

    // ── Shared formatters ────────────────────────────────────────────────────

    @Test
    fun `a duration shows hours only when there are hours`() {
        assertEquals("3:42", formatDuration(222_000L))
        assertEquals("1:42:07", formatDuration(6_127_000L))
        assertEquals("0:05", formatDuration(5_000L))
        assertEquals("", formatDuration(0L))
    }

    @Test
    fun `a relative date rolls over to a real date instead of counting forever`() {
        assertEquals("Today", relativeDate(now, now))
        assertEquals("Yesterday", relativeDate(now - day, now))
        assertEquals("9 days ago", relativeDate(now - 9 * day, now))
        assertEquals("30 days ago", relativeDate(now - 30 * day, now))
        // "412 days ago" is a number you have to do arithmetic on to understand.
        val old = now - 412 * day
        assertEquals(formatDate(old), relativeDate(old, now))
    }

    @Test
    fun `a timestamp in the future is shown as a date, never as negative days`() {
        // A restored backup or a device whose clock was wrong. "-3 days ago" renders fine.
        val future = now + 3 * day
        assertEquals(formatDate(future), relativeDate(future, now))
    }

    @Test
    fun `every library uses the same separator`() {
        // Books used one space around the dot and photos used two. Nobody can name that
        // difference and everybody sees it.
        val lines = listOfNotNull(
            musicRowSubtitle("a", "b", 1000L),
            videoRowSubtitle(1000L, now - day, now),
            bookRowSubtitle("a", "b", 1.0),
            photoRowSubtitle(now, "1×1", 2048L),
        )
        assertEquals(4, lines.size)
        lines.forEach { assertTrue(it, it.contains("  ·  ") && !it.contains(" · ·")) }
        lines.forEach { assertTrue("dangling separator in $it", !it.trim().endsWith("·")) }
    }
}
