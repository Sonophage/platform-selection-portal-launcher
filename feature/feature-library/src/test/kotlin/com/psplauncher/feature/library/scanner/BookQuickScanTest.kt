package com.psplauncher.feature.library.scanner

import com.psplauncher.core.domain.model.Book
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BookQuickScanTest {
    private fun book(
        lastModified: Long? = 1000L,
        title: String? = "Dune",
        coverUri: String? = "file:///cache/book_covers/abc.jpg",
    ) = Book(
        id = "b1",
        libraryId = "l1",
        uri = "content://doc/b1",
        displayName = "Dune.epub",
        title = title,
        coverUri = coverUri,
        lastModified = lastModified,
    )

    private val coverPresent: (String) -> Boolean = { true }
    private val coverGone: (String) -> Boolean = { false }

    @Test
    fun `an unchanged parsed book with its cover on disk is reused`() {
        assertTrue(canReuse(book(), lastModified = 1000L, coverExists = coverPresent))
    }

    @Test
    fun `a book whose file changed is reparsed`() {
        assertFalse(canReuse(book(lastModified = 1000L), lastModified = 2000L, coverExists = coverPresent))
    }

    @Test
    fun `a book the scanner has never seen is parsed`() {
        assertFalse(canReuse(null, lastModified = 1000L, coverExists = coverPresent))
    }

    @Test
    fun `a row from before the metadata pass is reparsed even though its file is unchanged`() {
        val unparsed = book(title = null, coverUri = null)
        assertFalse(canReuse(unparsed, lastModified = 1000L, coverExists = coverPresent))
    }

    @Test
    fun `a parsed book that genuinely has no cover is still reused`() {
        assertTrue(canReuse(book(coverUri = null), lastModified = 1000L, coverExists = coverGone))
    }

    @Test
    fun `clearing the cover cache makes the next scan regenerate covers`() {
        assertFalse(canReuse(book(), lastModified = 1000L, coverExists = coverGone))
    }

    @Test
    fun `a book known only by its series counts as parsed`() {
        val seriesOnly = book(title = null, coverUri = null).copy(series = "Dune")
        assertTrue(canReuse(seriesOnly, lastModified = 1000L, coverExists = coverPresent))
    }
}
