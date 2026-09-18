package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.Book
import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Sorting a book list by series, and the wiring that decides which list is being sorted.
 *
 * The second half matters more than the first. `cycleSort` and `currentSortLabel` both pick the
 * mode to read with a `when` whose last branch is `else -> gameSortMode`, so a section wired into
 * `activeSortModes` but missed in either `when` does not fail: it silently cycles the GAMES sort
 * mode and prints the games label while the user is looking at a shelf of books. That is three
 * places that have to agree with only the first one obviously load bearing, so the agreement is
 * asserted here rather than left to review.
 */
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

    // ── The comparator ────────────────────────────────────────────────────────

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
        // 2.5 is the whole reason series_index is a REAL column rather than an INTEGER.
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
        // Not every EPUB declares a series. Sorting them first would bury the series the user
        // switched to this mode to see under a wall of unrelated titles.
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

    // ── Series folders ────────────────────────────────────────────────────────

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
        // The Books row already lists everything, so a bucket holding the standalones would be a
        // second and worse copy of it. On this user's library that bucket would hold 44 of 80.
        val books = listOf(book("loose", "Neuromancer"), book("also", "Anathem"))
        assertEquals(emptyList<String>(), books.seriesGroups().map { it.name })
    }

    @Test
    fun `a series folder takes the cover of its earliest volume`() {
        // Not the first row in the list: the group is built from whatever order the query returned,
        // so the cover has to be chosen by series position or it changes between scans.
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
        // Alphabetically this is Children, Dune, Dune Messiah. Reading order is the point.
        assertEquals(listOf("a", "b", "c"), books.inSeriesOrder().map { it.id })
    }

    @Test
    fun `the flat list and the series folder agree on order within one series`() {
        // Both go through BY_SERIES_POSITION. If they ever diverge, the same three books read in
        // one order on the Books list and another inside their own folder, and each looks correct
        // on its own, so only comparing them catches it.
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

    // ── Which list sorts ──────────────────────────────────────────────────────

    @Test
    fun `a book list offers the book sort modes`() {
        val modes = state(BooksNav.AllBooks).activeSortModes()
        assertEquals(listOf(XmbSortMode.TITLE, XmbSortMode.SERIES, XmbSortMode.DATE_ADDED), modes)
        // Identity, not equality: cycleSort and currentSortLabel both branch on `cycle === X`, so
        // the list handed out here must be the same instance they compare against.
        assertSame(modes, state(BooksNav.Shelf("s1", "Shelf")).activeSortModes())
    }

    @Test
    fun `the Library root and the shelf list do not sort`() {
        // Both are fixed rows (the reader, Shelves, Books), not a library. Offering a sort there
        // would put a "Sort: Title" pill on a list whose order the user cannot change.
        assertNull(state(BooksNav.Root).activeSortModes())
        assertNull(state(BooksNav.Shelves).activeSortModes())
    }

    @Test
    fun `a series folder does not sort, because reading order is the point of it`() {
        // Offering Title here would let the user do the one thing the folder exists to prevent.
        assertNull(state(BooksNav.SeriesList).activeSortModes())
        assertNull(state(BooksNav.Series("Dune")).activeSortModes())
    }

    @Test
    fun `cycling a book list changes the book mode and leaves the game mode alone`() {
        // The failure this guards is silent: before sortModeFor/withSortMode existed, a section
        // missing from either `when` fell through to `else -> gameSortMode`, so pressing Square on
        // a shelf re-sorted the games list and left the books exactly where they were.
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
        // The enum is shared across sections, so this pins that the book cycle holds no music or
        // game mode: cycling onto ARTIST would print "Sort: Artist" over a list bookSorted() then
        // silently orders by title.
        val modes = state(BooksNav.AllBooks).activeSortModes().orEmpty()
        val unusable = listOf(XmbSortMode.ARTIST, XmbSortMode.ALBUM, XmbSortMode.RECENT_PLAYED)
        assertEquals(emptyList<XmbSortMode>(), modes.filter { it in unusable })
    }
}
