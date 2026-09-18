package com.psplauncher.core.data.book

import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.domain.model.Book
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The MIME rule is the one thing here that is not shared with music and video, and it exists
 * because of the same provider quirk that shapes [BookFileFilter]: most SAF providers report
 * `application/octet-stream` for `.epub`.
 *
 * That type is a provider saying "I do not know". Forwarding it to `ACTION_VIEW` resolves no
 * reader, so a book would refuse to open with no visible cause, for a file the library scanned
 * happily. The row only exists because the filter already decided it was an EPUB, so that is what
 * gets asserted on the way out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BookIntentResolverTest {

    private val resolver = BookIntentResolver(ApplicationProvider.getApplicationContext())

    private fun book(mime: String?) = Book(
        id = "b1",
        libraryId = "L1",
        uri = "content://com.example/tree/dune.epub",
        displayName = "dune.epub",
        mimeType = mime,
    )

    @Test
    fun `a provider that knows the type has it forwarded`() {
        assertEquals(
            "application/epub+zip",
            resolver.buildViewIntent(book("application/epub+zip"), null).type,
        )
    }

    @Test
    fun `octet-stream is replaced, not forwarded, or no reader resolves`() {
        assertEquals(
            BookFileFilter.EPUB_MIME,
            resolver.buildViewIntent(book("application/octet-stream"), null).type,
        )
    }

    @Test
    fun `a missing type falls back to epub, which is why the row exists`() {
        assertEquals(BookFileFilter.EPUB_MIME, resolver.buildViewIntent(book(null), null).type)
    }

    @Test
    fun `no reader chosen means no package pinned, so the chooser can run`() {
        assertNull(resolver.buildViewIntent(book(null), null).`package`)
        assertEquals(
            "com.flyersoft.moonreader",
            resolver.buildViewIntent(book(null), "com.flyersoft.moonreader").`package`,
        )
    }
}
