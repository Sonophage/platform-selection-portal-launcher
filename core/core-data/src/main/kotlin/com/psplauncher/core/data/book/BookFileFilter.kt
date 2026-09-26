package com.psplauncher.core.data.book

object BookFileFilter {
    const val EPUB_MIME = "application/epub+zip"

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
