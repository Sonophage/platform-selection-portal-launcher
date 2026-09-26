package com.psplauncher.core.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.database.entity.BookEntity
import com.psplauncher.core.data.database.entity.BookLibraryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BookCascadeTest {
    private val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        PFPDatabase::class.java,
    ).allowMainThreadQueries().build()

    private val libraryDao = db.bookLibraryDao()
    private val bookDao = db.bookDao()

    @After fun tearDown() = db.close()

    private fun library(id: String) = BookLibraryEntity(
        id = id,
        displayName = "Shelf $id",
        treeUri = "content://tree/$id",
        createdAt = 1,
        updatedAt = 1,
    )

    private fun book(id: String, libraryId: String) = BookEntity(
        id = id,
        libraryId = libraryId,
        uri = "content://tree/$libraryId/$id.epub",
        displayName = "$id.epub",
    )

    @Test
    fun `deleting a library deletes its books and leaves the other library alone`() = runTest {
        libraryDao.upsert(library("L1"))
        libraryDao.upsert(library("L2"))
        bookDao.insertAll(listOf(book("a", "L1"), book("b", "L1"), book("c", "L2")))

        libraryDao.delete("L1")

        assertEquals(emptyList(), bookDao.getForLibrary("L1"), "L1's books should have gone with it")
        assertEquals(1, bookDao.getForLibrary("L2").size, "L2 is a different library and must be untouched")
    }
}
