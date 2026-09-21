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
    fun `a video row names the running time, the resolution and the file size`() {
        assertEquals(
            "1:42:07  ·  1920×1080  ·  4.1 GB",
            videoRowSubtitle(6_127_000L, "1920×1080", 4_402_341_478L),
        )
    }

    @Test
    fun `a video row carries the same three facts, in the same order, as a photo row`() {
        // The whole reason this file exists. A duration stands where a photo has a date, then
        // resolution, then size. If these two ever drift apart again, the Video and Photo columns
        // go back to telling the user different amounts about the same kind of file.
        val video = videoRowSubtitle(6_127_000L, "1920×1080", 4_402_341_478L)!!
        val photo = photoRowSubtitle(now, "1920×1080", 4_402_341_478L)!!
        assertEquals(3, video.split("  ·  ").size)
        assertEquals(video.split("  ·  ").drop(1), photo.split("  ·  ").drop(1))
    }

    @Test
    fun `a video whose resolution was never read says only what it knows`() {
        // An unreadable or DRM-wrapped container yields a duration and nothing else. That must be
        // one clean fact, not a fact with two empty slots hanging off it.
        assertEquals("1:42:07", videoRowSubtitle(6_127_000L, null, null))
        assertEquals("1:42:07", videoRowSubtitle(6_127_000L, "   ", 0L))
    }

    @Test
    fun `a video the scanner could not read at all has no line, rather than an empty one`() {
        assertNull(videoRowSubtitle(null, null, null))
        assertNull(videoRowSubtitle(0L, "", 0L))
    }

    // NOTE ON WHAT LEFT THIS ROW. "Watched 3 days ago" used to sit in slot two, and the label was
    // deliberate: beside a running time a bare "3 days ago" reads just as easily as when the file
    // was added. That reasoning still holds. The slot was given up to the file facts so this row
    // would agree with the photo row, not because the label was wrong. If watched state returns
    // here it returns labelled, and `relativeDate` is still the function that formats it.

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
            videoRowSubtitle(1000L, "1×1", 2048L),
            bookRowSubtitle("a", "b", 1.0),
            photoRowSubtitle(now, "1×1", 2048L),
        )
        assertEquals(4, lines.size)
        lines.forEach { assertTrue(it, it.contains("  ·  ") && !it.contains(" · ·")) }
        lines.forEach { assertTrue("dangling separator in $it", !it.trim().endsWith("·")) }
    }

    // ── Counts ───────────────────────────────────────────────────────────────

    @Test
    fun `a count reads one game and three games`() {
        assertEquals("1 game", countLabel(1, "game", "games"))
        assertEquals("3 games", countLabel(3, "game", "games"))
    }

    @Test
    fun `none of it takes the singular`() {
        // An empty library is the state a new user meets first, and "0 game" would be the first
        // thing the app said to them.
        assertEquals("0 games", countLabel(0, "game", "games"))
    }

    @Test
    fun `the noun is never capitalised, whatever it counts`() {
        // THE DRIFT THIS REPLACES. Game and App rows were written Title Case and every other
        // library lowercase, so two D-pad presses apart the same widget in the same slot read
        // "3 Games" and then "0 libraries". One inline ternary per row is how that happened, so
        // the rule now has one home and this is its guard.
        val nouns = listOf(
            "game" to "games", "app" to "apps", "track" to "tracks", "video" to "videos",
            "library" to "libraries", "shelf" to "shelves", "book" to "books",
            "album" to "albums", "photo" to "photos", "series" to "series",
        )
        nouns.forEach { (singular, plural) ->
            listOf(0, 1, 2, 17).forEach { n ->
                val label = countLabel(n, singular, plural)
                val noun = label.substringAfter(' ')
                assertEquals("capitalised noun in \"$label\"", noun.lowercase(), noun)
            }
        }
    }

    @Test
    fun `an irregular plural is spelled out rather than guessed`() {
        // "shelfs" and "librarys" are why plural is a parameter. A rule with one silent exception
        // is not a rule, and the exception would only ever be seen on a device with two shelves.
        assertEquals("2 shelves", countLabel(2, "shelf", "shelves"))
        assertEquals("2 libraries", countLabel(2, "library", "libraries"))
        assertEquals("2 series", countLabel(2, "series", "series"))
    }

    @Test
    fun `the default plural is the regular one, so the common case cannot be got wrong`() {
        assertEquals("2 games", countLabel(2, "game"))
    }
}
