package com.psplauncher.feature.library.scanner

import com.psplauncher.core.domain.model.Book
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The quick-scan reuse rule.
 *
 * Reading a book's metadata means opening the archive up to three times and decoding an image, so
 * a rescan that reparses everything turns a settled library from seconds into minutes. That makes
 * this rule load bearing in both directions, and both directions fail quietly: too strict and
 * every rescan is a deep scan while still being labelled quick, too loose and a book's series and
 * cover never appear no matter how many times the user presses Rescan.
 *
 * The third case below is the one that is easy to get wrong. Rows written before covers existed
 * carry a null cover, which is also what a book with no cover art carries, so "has a cover" cannot
 * be the test for "has been parsed".
 */
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
        // Every book scanned on v44 looks exactly like this: a file name, a timestamp, and nothing
        // else. Without this rule the user's whole existing library would stay blank until they
        // found the Deep Rescan row.
        val unparsed = book(title = null, coverUri = null)
        assertFalse(canReuse(unparsed, lastModified = 1000L, coverExists = coverPresent))
    }

    @Test
    fun `a parsed book that genuinely has no cover is still reused`() {
        // Distinguished from the case above by having a title: the metadata pass ran, the book
        // simply declares no cover art. Reparsing this one on every rescan would be the strict
        // failure, and it is invisible except as a slow scan.
        assertTrue(canReuse(book(coverUri = null), lastModified = 1000L, coverExists = coverGone))
    }

    @Test
    fun `clearing the cover cache makes the next scan regenerate covers`() {
        // The row is unchanged and parsed, so only the missing file can force the reparse. If this
        // passed, Clear Cover Cache followed by Rescan would report success and change nothing.
        assertFalse(canReuse(book(), lastModified = 1000L, coverExists = coverGone))
    }

    @Test
    fun `a book known only by its series counts as parsed`() {
        // hasParsedMetadata reads four fields because an EPUB is obliged to carry none of them in
        // particular; a book with a series and no title must not be reparsed forever.
        val seriesOnly = book(title = null, coverUri = null).copy(series = "Dune")
        assertTrue(canReuse(seriesOnly, lastModified = 1000L, coverExists = coverPresent))
    }
}
