package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.Book
import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class XmbBookSortTest {
    private fun book(
        id: String,
        title: String,
        series: String? = null,
        index: Double? = null,
        added: Long? = null,
    ) = Book(
        id = id,
        libraryId = "l1",
        uri = "content://doc/$id",
        displayName = "$title.epub",
        title = title,
        series = series,
        seriesIndex = index,
        dateAdded = added,
    )

    private fun state(nav: BooksNav) = XMBUiState(
        categories = listOf(
            Category(
                id = BuiltInCategory.LIBRARY,
                name = "Library",
                iconKey = "library",
                type = CategoryType.BUILT_IN,
                position = 0,
            ),
        ),
        selectedCategoryIndex = 0,
        booksNav = nav,
    )

    @Test
    fun `SERIES groups a series together and puts it in reading order`() {
        val books = listOf(
            book("3", "Children of Dune", series = "Dune", index = 3.0),
            book("1", "Dune", series = "Dune", index = 1.0),
            book("h1", "Hyperion", series = "Hyperion", index = 1.0),
            book("2", "Dune Messiah", series = "Dune", index = 2.0),
        )
        val sorted = books.bookSorted(XmbSortMode.SERIES).map { it.id }
        assertEquals(listOf("1", "2", "3", "h1"), sorted)
    }

    @Test
    fun `SERIES keeps a half-numbered novella between the books it sits between`() {
        val books = listOf(
            book("3", "Book Three", series = "S", index = 3.0),
            book("2", "Book Two", series = "S", index = 2.0),
            book("novella", "The Novella", series = "S", index = 2.5),
        )
        val sorted = books.bookSorted(XmbSortMode.SERIES).map { it.id }
        assertEquals(listOf("2", "novella", "3"), sorted)
    }

    @Test
    fun `SERIES puts books with no series last, not first`() {
        val books = listOf(
            book("loose", "Neuromancer"),
            book("d1", "Dune", series = "Dune", index = 1.0),
        )
        assertEquals(listOf("d1", "loose"), books.bookSorted(XmbSortMode.SERIES).map { it.id })
    }

    @Test
    fun `SERIES sorts an unnumbered entry after the numbered ones in its series`() {
        val books = listOf(
            book("x", "Unnumbered Companion", series = "Dune"),
            book("d1", "Dune", series = "Dune", index = 1.0),
        )
        assertEquals(listOf("d1", "x"), books.bookSorted(XmbSortMode.SERIES).map { it.id })
    }

    @Test
    fun `TITLE ignores the series entirely`() {
        val books = listOf(
            book("b", "Zenith", series = "AAA", index = 1.0),
            book("a", "Apex", series = "ZZZ", index = 1.0),
        )
        assertEquals(listOf("a", "b"), books.bookSorted(XmbSortMode.TITLE).map { it.id })
    }

    @Test
    fun `DATE_ADDED sorts newest first`() {
        val books = listOf(book("old", "A", added = 100), book("new", "B", added = 900))
        assertEquals(listOf("new", "old"), books.bookSorted(XmbSortMode.DATE_ADDED).map { it.id })
    }

    @Test
    fun `series are grouped by name, counted, and listed alphabetically`() {
        val books = listOf(
            book("h1", "Hyperion", series = "Hyperion", index = 1.0),
            book("d1", "Dune", series = "Dune", index = 1.0),
            book("d2", "Dune Messiah", series = "Dune", index = 2.0),
            book("loose", "Neuromancer"),
        )
        val groups = books.seriesGroups()
        assertEquals(listOf("Dune", "Hyperion"), groups.map { it.name })
        assertEquals(listOf(2, 1), groups.map { it.bookCount })
    }

    @Test
    fun `a book with no series joins no group rather than a No series bucket`() {
        val books = listOf(book("loose", "Neuromancer"), book("also", "Anathem"))
        assertEquals(emptyList<String>(), books.seriesGroups().map { it.name })
    }

    @Test
    fun `a series folder takes the cover of its earliest volume`() {
        val books = listOf(
            book("d3", "Children of Dune", series = "Dune", index = 3.0).copy(coverUri = "file:///three.jpg"),
            book("d1", "Dune", series = "Dune", index = 1.0).copy(coverUri = "file:///one.jpg"),
        )
        assertEquals("file:///one.jpg", books.seriesGroups().single().coverUri)
    }

    @Test
    fun `a series folder falls through to a later cover when the first volume has none`() {
        val books = listOf(
            book("d1", "Dune", series = "Dune", index = 1.0),
            book("d2", "Dune Messiah", series = "Dune", index = 2.0).copy(coverUri = "file:///two.jpg"),
        )
        assertEquals("file:///two.jpg", books.seriesGroups().single().coverUri)
    }

    @Test
    fun `a series folder lists in reading order, never alphabetically`() {
        val books = listOf(
            book("c", "Children of Dune", series = "Dune", index = 3.0),
            book("a", "Dune", series = "Dune", index = 1.0),
            book("b", "Dune Messiah", series = "Dune", index = 2.0),
        )

        assertEquals(listOf("a", "b", "c"), books.inSeriesOrder().map { it.id })
    }

    @Test
    fun `the flat list and the series folder agree on order within one series`() {
        val dune = listOf(
            book("c", "Children of Dune", series = "Dune", index = 3.0),
            book("a", "Dune", series = "Dune", index = 1.0),
            book("b", "Dune Messiah", series = "Dune", index = 2.0),
        )
        assertEquals(
            dune.bookSorted(XmbSortMode.SERIES).map { it.id },
            dune.inSeriesOrder().map { it.id },
        )
    }

    @Test
    fun `a book list offers the book sort modes`() {
        val modes = state(BooksNav.AllBooks).activeSortModes()
        assertEquals(listOf(XmbSortMode.TITLE, XmbSortMode.SERIES, XmbSortMode.DATE_ADDED), modes)

        assertSame(modes, state(BooksNav.Shelf("s1", "Shelf")).activeSortModes())
    }

    @Test
    fun `the Library root and the shelf list do not sort`() {
        assertNull(state(BooksNav.Root).activeSortModes())
        assertNull(state(BooksNav.Shelves).activeSortModes())
    }

    @Test
    fun `a series folder does not sort, because reading order is the point of it`() {
        assertNull(state(BooksNav.SeriesList).activeSortModes())
        assertNull(state(BooksNav.Series("Dune")).activeSortModes())
    }

    @Test
    fun `cycling a book list changes the book mode and leaves the game mode alone`() {
        val cycle = state(BooksNav.AllBooks).activeSortModes()!!
        val after = state(BooksNav.AllBooks).withSortMode(cycle, XmbSortMode.SERIES)

        assertEquals(XmbSortMode.SERIES, after.sortModeFor(cycle))
        assertEquals(XmbSortMode.SERIES, after.bookSortMode)
        assertEquals(XmbSortMode.TITLE, after.gameSortMode)
        assertEquals(XmbSortMode.TITLE, after.musicSortMode)
        assertEquals(XmbSortMode.TITLE, after.videoSortMode)
    }

    @Test
    fun `a book sort mode is never one the book list cannot use`() {
        val modes = state(BooksNav.AllBooks).activeSortModes().orEmpty()
        val unusable = listOf(XmbSortMode.ARTIST, XmbSortMode.ALBUM, XmbSortMode.RECENT_PLAYED)
        assertEquals(emptyList<XmbSortMode>(), modes.filter { it in unusable })
    }
}
