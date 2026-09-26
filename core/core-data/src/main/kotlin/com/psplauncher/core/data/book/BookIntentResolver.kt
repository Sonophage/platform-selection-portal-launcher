package com.psplauncher.core.data.book

import android.content.Context
import android.content.Intent
import com.psplauncher.core.common.launch.LaunchTransition
import com.psplauncher.core.common.launch.LaunchTransition.withoutTransition
import com.psplauncher.core.data.media.MediaOpenIntent
import com.psplauncher.core.domain.model.Book
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** An installed app able to handle ACTION_VIEW for EPUB — a candidate default reader. */
data class ReaderApp(
    val packageName: String,
    val label: String,
)

/**
 * Hands a book to a reader app. The Android half is [MediaOpenIntent], shared with music and
 * video; what is here is what is about books.
 *
 * The MIME is the one place this differs in kind from its siblings. A scanned book may carry
 * `application/octet-stream`, because that is what most SAF providers return for `.epub`, and
 * sending that to a reader resolves nothing. So the stored type is used only when the provider
 * actually recognised the file; otherwise this asserts [BookFileFilter.EPUB_MIME], which is what
 * got the row into the library in the first place.
 */
@Singleton
class BookIntentResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun buildViewIntent(book: Book, readerPackage: String?): Intent =
        MediaOpenIntent.build(
            uri = book.uri,
            mimeType = openableMimeOf(book),
            pinnedPackage = readerPackage,
        )

    /**
     * Opens [book] in [readerPackage], or offers the chooser when none is set. Returns a
     * user-readable message on failure, or null on success. Never throws.
     */
    fun launch(book: Book, readerPackage: String?): String? =
        MediaOpenIntent.launch(
            context = context,
            intent = buildViewIntent(book, readerPackage),
            chooserTitle = CHOOSER_TITLE,
            noHandlerMessage =
                "No reader could open this book. Install one, or pick it in Settings → Library.",
            logLabel = "book \"${book.displayTitle}\"",
        )

    /** Shows the system chooser for [book] ("Ask Every Time"). */
    fun launchChooser(book: Book): String? =
        MediaOpenIntent.launchChooser(
            context = context,
            intent = buildViewIntent(book, null),
            chooserTitle = CHOOSER_TITLE,
            noHandlerMessage = "No reader is installed.",
            logLabel = "book \"${book.displayTitle}\"",
        )

    /** Display label for an installed reader, or null when it is not installed. */
    fun readerLabel(packageName: String): String? = MediaOpenIntent.label(context, packageName)

    /** Installed apps that can handle ACTION_VIEW for EPUB, one per package, sorted by label. */
    fun availableReaders(): List<ReaderApp> =
        MediaOpenIntent.handlers(context, BookFileFilter.EPUB_MIME)
            .map { ReaderApp(packageName = it.packageName, label = it.label) }

    /**
     * Opens the reader with no book. This is what the section's first row does, so the app you
     * read in is one press away whether or not you are opening something from the library.
     */
    fun launchReader(packageName: String): String? {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.withoutTransition()
            ?: return "${readerLabel(packageName) ?: "That reader"} is not installed."
        return try {
            context.startActivity(intent, LaunchTransition.options(context))
            null
        } catch (e: Exception) {
            "${readerLabel(packageName) ?: "That reader"} could not be opened."
        }
    }

    private companion object {
        const val CHOOSER_TITLE = "Open book with…"

        /**
         * `application/octet-stream` is a provider saying "I do not know", not a type any reader
         * advertises, so it must not be forwarded.
         */
        fun openableMimeOf(book: Book): String =
            book.mimeType?.takeIf { it != "application/octet-stream" } ?: BookFileFilter.EPUB_MIME
    }
}
