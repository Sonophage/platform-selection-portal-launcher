package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRowTextTest {
    private val day = 86_400_000L
    private val now = 1_760_000_000_000L

    @Test
    fun `a music row names the artist, the album and the running time`() {
        assertEquals(
            "Konami  ·  Greatest Hits  ·  3:42",
            musicRowSubtitle("Konami", "Greatest Hits", 222_000L),
        )
    }

    @Test
    fun `the album is carried, because nothing else in the app shows it`() {
        assertTrue(musicRowSubtitle(null, "Greatest Hits", null)!!.contains("Greatest Hits"))
    }

    @Test
    fun `a track with nothing scanned has no line at all`() {
        assertNull(musicRowSubtitle(null, null, null))
        assertNull(musicRowSubtitle("", "   ", 0L))
    }

    @Test
    fun `a video row names the running time, the resolution and the file size`() {
        assertEquals(
            "1:42:07  ·  1920×1080  ·  4.1 GB",
            videoRowSubtitle(6_127_000L, "1920×1080", 4_402_341_478L),
        )
    }

    @Test
    fun `a video row carries the same three facts, in the same order, as a photo row`() {
        val video = videoRowSubtitle(6_127_000L, "1920×1080", 4_402_341_478L)!!
        val photo = photoRowSubtitle(now, "1920×1080", 4_402_341_478L)!!
        assertEquals(3, video.split("  ·  ").size)
        assertEquals(video.split("  ·  ").drop(1), photo.split("  ·  ").drop(1))
    }

    @Test
    fun `a video whose resolution was never read says only what it knows`() {
        assertEquals("1:42:07", videoRowSubtitle(6_127_000L, null, null))
        assertEquals("1:42:07", videoRowSubtitle(6_127_000L, "   ", 0L))
    }

    @Test
    fun `a video the scanner could not read at all has no line, rather than an empty one`() {
        assertNull(videoRowSubtitle(null, null, null))
        assertNull(videoRowSubtitle(0L, "", 0L))
    }

    @Test
    fun `a book row names the author, then where it falls in its series`() {
        assertEquals(
            "Frank Herbert  ·  Dune #2",
            bookRowSubtitle("Frank Herbert", "Dune", 2.0),
        )
    }

    @Test
    fun `a whole-numbered series entry drops its decimal, a half keeps it`() {
        assertTrue(bookRowSubtitle(null, "Dune", 2.0)!!.endsWith("#2"))
        assertTrue(bookRowSubtitle(null, "Dune", 2.5)!!.endsWith("#2.5"))
    }

    @Test
    fun `a standalone book is just its author, with no empty series marker`() {
        assertEquals("Ursula K Le Guin", bookRowSubtitle("Ursula K Le Guin", null, null))

        assertEquals("Ursula K Le Guin", bookRowSubtitle("Ursula K Le Guin", "   ", 1.0))
    }

    @Test
    fun `a photo row names the date, the resolution and the file size`() {
        val line = photoRowSubtitle(now, "4032×3024", 3_355_443L)!!
        assertTrue(line, line.contains("4032×3024"))
        assertTrue(line, line.contains("3.2 MB"))

        assertTrue(line, line.indexOf("4032") > 0 && line.startsWith(formatDate(now)))
    }

    @Test
    fun `a zero size is omitted rather than drawn as a dash`() {
        assertEquals("4032×3024", photoRowSubtitle(null, "4032×3024", 0L))
    }

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

        val old = now - 412 * day
        assertEquals(formatDate(old), relativeDate(old, now))
    }

    @Test
    fun `a timestamp in the future is shown as a date, never as negative days`() {
        val future = now + 3 * day
        assertEquals(formatDate(future), relativeDate(future, now))
    }

    @Test
    fun `every library uses the same separator`() {
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

    @Test
    fun `a count reads one game and three games`() {
        assertEquals("1 game", countLabel(1, "game", "games"))
        assertEquals("3 games", countLabel(3, "game", "games"))
    }

    @Test
    fun `none of it takes the singular`() {
        assertEquals("0 games", countLabel(0, "game", "games"))
    }

    @Test
    fun `the noun is never capitalised, whatever it counts`() {
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
        assertEquals("2 shelves", countLabel(2, "shelf", "shelves"))
        assertEquals("2 libraries", countLabel(2, "library", "libraries"))
        assertEquals("2 series", countLabel(2, "series", "series"))
    }

    @Test
    fun `the default plural is the regular one, so the common case cannot be got wrong`() {
        assertEquals("2 games", countLabel(2, "game"))
    }

    @Test
    fun `a played game shows the system and when it was played`() {
        val now = 1_700_000_000_000L
        val day = 86_400_000L
        assertEquals(
            "Game Boy Advance  ·  Yesterday",
            gameMetaLine("Game Boy Advance", now - day, "Nintendo", now),
        )

        assertEquals(
            "Nintendo DS  ·  9 days ago",
            gameMetaLine("Nintendo DS", now - 9 * day, "Konami", now),
        )
    }

    @Test
    fun `today carries a clock time and nothing else does`() {
        val now = 1_700_000_000_000L
        val today = gameMetaLine("Game Boy Advance", now, "Nintendo", now)
        assertTrue("today should carry a time, got: $today", today.startsWith("Game Boy Advance  ·  Today, "))
        assertTrue("today's time should not be empty", today.length > "Game Boy Advance  ·  Today, ".length)

        assertEquals(
            "Game Boy Advance  ·  Yesterday",
            gameMetaLine("Game Boy Advance", now - 86_400_000L, null, now),
        )
    }

    @Test
    fun `an unplayed game falls back to its publisher`() {
        val now = 1_700_000_000_000L
        assertEquals(
            "Game Boy Advance  ·  Nintendo",
            gameMetaLine("Game Boy Advance", null, "Nintendo", now),
        )
    }

    @Test
    fun `a zero timestamp is never played, not 1970`() {
        val now = 1_700_000_000_000L
        assertEquals(
            "Game Boy Advance  ·  Nintendo",
            gameMetaLine("Game Boy Advance", 0L, "Nintendo", now),
        )
    }

    @Test
    fun `with neither a play record nor a publisher the separator goes too`() {
        val now = 1_700_000_000_000L
        assertEquals("Game Boy Advance", gameMetaLine("Game Boy Advance", null, null, now))
        assertEquals("Game Boy Advance", gameMetaLine("Game Boy Advance", null, "   ", now))
        assertEquals("Game Boy Advance", gameMetaLine("Game Boy Advance", 0L, "", now))
    }
}
