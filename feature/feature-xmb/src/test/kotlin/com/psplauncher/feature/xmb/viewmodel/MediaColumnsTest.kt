package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BookLibrary
import com.psplauncher.core.domain.model.MusicFolder
import com.psplauncher.core.domain.model.MusicTrack
import com.psplauncher.core.domain.model.PhotoLibrary
import com.psplauncher.core.domain.model.VideoLibrary
import com.psplauncher.feature.xmb.music.MusicPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What each media column offers at its root.
 *
 * The rows themselves are about to be restructured -- Music gains Artists and Albums, Video puts
 * Videos first -- and until now the only way to see a column's shape was to open it on the device.
 * The counts matter as much as the order: a subtitle reading "1 libraries" is the kind of thing
 * nobody notices in a screenshot.
 */
class MediaColumnsTest {

    private fun musicFolder(tracks: Int) = MusicFolder(
        id = "f$tracks", displayName = "Music", treeUri = "content://f",
        trackCount = tracks, createdAt = 0L, updatedAt = 0L,
    )

    private fun videoLibrary(videos: Int) = VideoLibrary(
        id = "v$videos", displayName = "Films", treeUri = "content://v",
        videoCount = videos, createdAt = 0L, updatedAt = 0L,
    )

    private fun photoLibrary(photos: Int) = PhotoLibrary(
        id = "p$photos", displayName = "Camera Roll", treeUri = "content://p",
        photoCount = photos, createdAt = 0L, updatedAt = 0L,
    )

    private fun bookLibrary(books: Int) = BookLibrary(
        id = "b$books", displayName = "Shelf", treeUri = "content://b",
        bookCount = books, createdAt = 0L, updatedAt = 0L,
    )

    private fun ids(items: List<XMBItem>) = items.map { it.id }

    private fun subtitleOf(items: List<XMBItem>, id: String) = items.first { it.id == id }.subtitle

    // ── Music ─────────────────────────────────────────────────────────────

    @Test
    fun `music root is playlist then all music, in that order`() {
        assertEquals(
            listOf("playlists", "all_music"),
            ids(XMBUiState().musicRootSections()),
        )
    }

    @Test
    fun `now playing appears only while a track is loaded, and leads`() {
        // It is the way back to the song you are listening to. Below the fold it is useless, and
        // with nothing playing it opens a player showing nothing.
        val playing = XMBUiState(
            musicPlayback = MusicPlaybackState(
                track = MusicTrack(
                    id = "t1", folderId = "f1", uri = "content://t1",
                    displayName = "01 Those Who Fight.mp3",
                    title = "Those Who Fight", artist = "Nobuo Uematsu",
                ),
            ),
        )
        assertEquals("now_playing", ids(playing.musicRootSections()).first())
        assertFalse("now_playing" in ids(XMBUiState().musicRootSections()))
    }

    @Test
    fun `the music card counts every folder's tracks, not the folders`() {
        val state = XMBUiState(musicFolders = listOf(musicFolder(12), musicFolder(30)))
        assertEquals("42 tracks", subtitleOf(state.musicRootSections(), "all_music"))
    }

    // ── Video ─────────────────────────────────────────────────────────────

    @Test
    fun `video root is collections, libraries, then all videos`() {
        assertEquals(
            listOf("video_collections", "video_libraries", "all_videos"),
            ids(XMBUiState().videoRootSections()),
        )
    }

    @Test
    fun `video counts the libraries on one row and their videos on the other`() {
        val state = XMBUiState(videoLibraries = listOf(videoLibrary(3), videoLibrary(4)))
        val rows = state.videoRootSections()
        assertEquals("2 libraries", subtitleOf(rows, "video_libraries"))
        assertEquals("7 videos", subtitleOf(rows, "all_videos"))
    }

    // ── Photo ─────────────────────────────────────────────────────────────

    @Test
    fun `camera shows only when the device has a camera app`() {
        // The row hands off to an intent; with nothing to resolve it, pressing it does nothing.
        assertEquals("photo_camera", ids(XMBUiState().photoRootSections(true)).first())
        assertFalse("photo_camera" in ids(XMBUiState().photoRootSections(false)))
    }

    @Test
    fun `photo root is albums then all photos once the camera is out of the way`() {
        assertEquals(
            listOf("photo_albums", "all_photos"),
            ids(XMBUiState().photoRootSections(false)),
        )
    }

    // ── Library ───────────────────────────────────────────────────────────

    @Test
    fun `the reader row appears only when a reader is set, and leads`() {
        val withReader = XMBUiState(defaultReader = "org.readera", defaultReaderLabel = "ReadEra")
        assertEquals("library_open_reader", ids(withReader.booksRootSections()).first())
        assertEquals("ReadEra", withReader.booksRootSections().first().title)
        assertFalse("library_open_reader" in ids(XMBUiState().booksRootSections()))
    }

    @Test
    fun `shelves is a row only once there is more than one shelf`() {
        // With a single shelf the row opens a list of one holding exactly what Books already
        // holds -- two presses to arrive nowhere new.
        val one = XMBUiState(bookLibraries = listOf(bookLibrary(5)))
        val two = XMBUiState(bookLibraries = listOf(bookLibrary(5), bookLibrary(6)))
        assertFalse("library_shelves" in ids(one.booksRootSections()))
        assertTrue("library_shelves" in ids(two.booksRootSections()))
        assertEquals("2 shelves", subtitleOf(two.booksRootSections(), "library_shelves"))
    }

    @Test
    fun `series is a row only once something declares one`() {
        val none = XMBUiState(bookLibraries = listOf(bookLibrary(5)))
        val some = none.copy(bookSeries = listOf(BookSeries("Discworld", 41, null)))
        assertFalse("library_series" in ids(none.booksRootSections()))
        assertEquals("1 series", subtitleOf(some.booksRootSections(), "library_series"))
    }

    @Test
    fun `books is always last`() {
        val full = XMBUiState(
            bookLibraries = listOf(bookLibrary(5), bookLibrary(6)),
            bookSeries = listOf(BookSeries("Discworld", 41, null)),
            defaultReader = "org.readera",
        )
        assertEquals("all_books", ids(full.booksRootSections()).last())
        assertEquals("all_books", ids(XMBUiState().booksRootSections()).last())
        assertEquals("11 books", subtitleOf(full.booksRootSections(), "all_books"))
    }

    // ── The Add row ───────────────────────────────────────────────────────

    @Test
    fun `one add row is shown as itself and several collapse into one menu`() {
        val folder = XMBItem(id = "add_music_folder", title = "Add Music Folder")
        val apps = XMBItem(id = "add_music_apps", title = "Add Music Apps")
        assertEquals(emptyList<XMBItem>(), collapseAddRows(emptyList()))
        assertEquals(listOf(folder), collapseAddRows(listOf(folder)))

        val collapsed = collapseAddRows(listOf(folder, apps))
        assertEquals(listOf("add_menu"), ids(collapsed))
        // The submenu's contents, minus the "Add " every one of them starts with -- the row
        // already says Add, and repeating it makes the subtitle read as a stutter.
        assertEquals("Music Folder  ·  Music Apps", collapsed.single().subtitle)
    }
}
