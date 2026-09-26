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

data class ReaderApp(
    val packageName: String,
    val label: String,
)

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

    fun launch(book: Book, readerPackage: String?): String? =
        MediaOpenIntent.launch(
            context = context,
            intent = buildViewIntent(book, readerPackage),
            chooserTitle = CHOOSER_TITLE,
            noHandlerMessage =
                "No reader could open this book. Install one, or pick it in Settings → Library.",
            logLabel = "book \"${book.displayTitle}\"",
        )

    fun launchChooser(book: Book): String? =
        MediaOpenIntent.launchChooser(
            context = context,
            intent = buildViewIntent(book, null),
            chooserTitle = CHOOSER_TITLE,
            noHandlerMessage = "No reader is installed.",
            logLabel = "book \"${book.displayTitle}\"",
        )

    fun readerLabel(packageName: String): String? = MediaOpenIntent.label(context, packageName)

    fun availableReaders(): List<ReaderApp> =
        MediaOpenIntent.handlers(context, BookFileFilter.EPUB_MIME)
            .map { ReaderApp(packageName = it.packageName, label = it.label) }

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

        fun openableMimeOf(book: Book): String =
            book.mimeType?.takeIf { it != "application/octet-stream" } ?: BookFileFilter.EPUB_MIME
    }
}
