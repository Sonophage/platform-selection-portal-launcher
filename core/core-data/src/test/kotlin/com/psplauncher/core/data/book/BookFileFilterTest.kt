package com.psplauncher.core.data.book

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * SAF providers report `application/octet-stream` for `.epub` more often than they report the real
 * type, so a MIME-only gate finds an empty library and reads to the user as a broken scan. The
 * extension fallback is the reason this object exists, not a nicety, which is why it has more
 * cases here than the happy path does.
 *
 * Mirrors the shape [com.psplauncher.core.data.photo.PhotoFileFilter] already uses for the same
 * problem with images.
 */
class BookFileFilterTest {

    @Test
    fun `the real epub mime is accepted`() =
        assertTrue(BookFileFilter.isBook("Dune.epub", "application/epub+zip"))

    @Test
    fun `an octet-stream epub is accepted on its extension`() =
        assertTrue(BookFileFilter.isBook("Dune.epub", "application/octet-stream"))

    @Test
    fun `a missing mime falls back to the extension, case insensitively`() {
        assertTrue(BookFileFilter.isBook("Dune.EPUB", null))
        assertTrue(BookFileFilter.isBook("Dune.EpUb", null))
    }

    @Test
    fun `a file the provider types as something else is refused`() {
        assertFalse(BookFileFilter.isBook("cover.jpg", "image/jpeg"))
        assertFalse(BookFileFilter.isBook("audiobook.m4b", "audio/mp4"))
    }

    @Test
    fun `an unknown extension under octet-stream is refused rather than guessed`() =
        assertFalse(BookFileFilter.isBook("notes.txt", "application/octet-stream"))

    @Test
    fun `an extensionless file is refused rather than guessed`() =
        assertFalse(BookFileFilter.isBook("Dune", null))

    @Test
    fun `a dotfile is not read as an extension`() =
        assertFalse(BookFileFilter.isBook(".epub", null))
}
