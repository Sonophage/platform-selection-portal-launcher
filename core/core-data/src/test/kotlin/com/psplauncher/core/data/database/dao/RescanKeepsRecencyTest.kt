package com.psplauncher.core.data.database.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.psplauncher.core.data.database.PFPDatabase
import com.psplauncher.core.data.database.entity.BookEntity
import com.psplauncher.core.data.database.entity.BookLibraryEntity
import com.psplauncher.core.data.database.entity.MusicFolderEntity
import com.psplauncher.core.data.database.entity.MusicTrackEntity
import com.psplauncher.core.data.database.entity.VideoEntity
import com.psplauncher.core.data.database.entity.VideoLibraryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RescanKeepsRecencyTest {
    private lateinit var db: PFPDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            PFPDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun track(id: String, folderId: String) =
        MusicTrackEntity(id = id, folderId = folderId, uri = "file:///m/$id.flac", displayName = id)

    private fun book(id: String, libraryId: String) =
        BookEntity(id = id, libraryId = libraryId, uri = "file:///b/$id.epub", displayName = id)

    private fun video(id: String, libraryId: String) =
        VideoEntity(id = id, libraryId = libraryId, uri = "file:///v/$id.mkv", displayName = id)

    @Test
    fun `rescanning a music folder keeps what was played`() = runTest {
        val dao = db.musicTrackDao()
        db.musicFolderDao().upsert(
            MusicFolderEntity(id = "f1", displayName = "Music", treeUri = "file:///m", createdAt = 0, updatedAt = 0),
        )
        dao.replaceForFolder("f1", listOf(track("t1", "f1"), track("t2", "f1")))
        dao.markPlayed("t1", 5_000L)

        dao.replaceForFolder("f1", listOf(track("t1", "f1"), track("t2", "f1")))

        assertEquals(
            "a rescan wiped last_played_at, so the shelf empties itself for no visible reason",
            5_000L,
            dao.getById("t1")?.lastPlayedAt,
        )
        assertEquals("a track never played must not acquire a stamp", null, dao.getById("t2")?.lastPlayedAt)
    }

    @Test
    fun `rescanning a book library keeps what was opened`() = runTest {
        val dao = db.bookDao()
        db.bookLibraryDao().upsert(
            BookLibraryEntity(id = "l1", displayName = "Books", treeUri = "file:///b", createdAt = 0, updatedAt = 0),
        )
        dao.replaceForLibrary("l1", listOf(book("b1", "l1"), book("b2", "l1")))
        dao.markOpened("b1", 7_000L)

        dao.replaceForLibrary("l1", listOf(book("b1", "l1"), book("b2", "l1")))

        assertEquals(7_000L, dao.getById("b1")?.lastOpenedAt)
        assertEquals(null, dao.getById("b2")?.lastOpenedAt)
    }

    @Test
    fun `rescanning a video library keeps the watch stamp AND the resume position`() = runTest {
        val dao = db.videoDao()
        db.videoLibraryDao().upsert(
            VideoLibraryEntity(id = "l1", displayName = "Video", treeUri = "file:///v", createdAt = 0, updatedAt = 0),
        )
        dao.replaceForLibrary("l1", listOf(video("v1", "l1"), video("v2", "l1")))
        dao.updateResumePosition("v1", positionMs = 1_800_000L, watchedAt = 9_000L)

        dao.replaceForLibrary("l1", listOf(video("v1", "l1"), video("v2", "l1")))

        val restored = dao.getById("v1")
        assertEquals("the rescan restarted a part-watched film", 1_800_000L, restored?.resumePositionMs)
        assertEquals(9_000L, restored?.lastWatchedAt)
        assertEquals(0L, dao.getById("v2")?.resumePositionMs)
    }

    @Test
    fun `a row that has gone from disk does not come back`() = runTest {
        val dao = db.musicTrackDao()
        db.musicFolderDao().upsert(
            MusicFolderEntity(id = "f1", displayName = "Music", treeUri = "file:///m", createdAt = 0, updatedAt = 0),
        )
        dao.replaceForFolder("f1", listOf(track("t1", "f1"), track("t2", "f1")))
        dao.markPlayed("t1", 5_000L)

        dao.replaceForFolder("f1", listOf(track("t2", "f1")))

        assertEquals(null, dao.getById("t1"))
    }
}
