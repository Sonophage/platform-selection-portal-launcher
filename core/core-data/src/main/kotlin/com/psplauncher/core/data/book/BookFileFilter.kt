package com.psplauncher.core.data.book

/**
 * Pure book-file detection shared by the scanner. A file counts as a book when the provider types
 * it as one, or — when the provider gives no type, or the generic `application/octet-stream` it
 * hands back for `.epub` most of the time — when its extension says so. Kept free of Android types
 * so it can be unit-tested directly.
 *
 * Mirrors [com.psplauncher.core.data.photo.PhotoFileFilter] with one deliberate difference: the
 * extension is read only from a dot that has a name in front of it, so a hidden file called
 * `.epub` is a dotfile rather than a book. PhotoFileFilter takes the simpler
 * `substringAfterLast('.')`, which would call `.jpg` an image.
 */
object BookFileFilter {

    const val EPUB_MIME = "application/epub+zip"

    /** What most SAF providers return for a file type they do not recognise. */
    private const val GENERIC_MIME = "application/octet-stream"

    val BOOK_EXTENSIONS = setOf("epub")

    private val BOOK_MIMES = setOf(EPUB_MIME)

    fun isBook(fileName: String, mimeType: String?): Boolean {
        if (mimeType != null && mimeType != GENERIC_MIME) return mimeType in BOOK_MIMES
        return extensionOf(fileName) in BOOK_EXTENSIONS
    }

    private fun extensionOf(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(dot + 1).lowercase() else ""
    }
}
