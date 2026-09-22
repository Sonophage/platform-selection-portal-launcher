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
    fun `music root is songs, artists, albums, playlists, in that order`() {
        // Everything first, then the two ways of cutting it, then the lists you build yourself.
        assertEquals(
            listOf("all_music", "music_artists", "music_albums", "playlists"),
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
    fun `video root leads with every video, the same order music reads in`() {
        assertEquals(
            listOf("all_videos", "video_collections", "video_libraries"),
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
    fun `photo root is all photos then albums once the camera is out of the way`() {
        assertEquals(
            listOf("all_photos", "photo_albums"),
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

    // ── Artists and Albums ────────────────────────────────────────────────

    private fun track(
        id: String, title: String, artist: String? = null, album: String? = null,
        artUri: String? = null,
    ) = MusicTrack(
        id = id, folderId = "f1", uri = "content://$id", displayName = "$id.mp3",
        title = title, artist = artist, album = album, artUri = artUri,
    )

    @Test
    fun `an artist is one row however many tracks it has`() {
        val groups = listOf(
            track("1", "Aerith's Theme", artist = "Nobuo Uematsu"),
            track("2", "One-Winged Angel", artist = "Nobuo Uematsu"),
            track("3", "Snake Eater", artist = "Norihiko Hibino"),
        ).artistGroups()
        assertEquals(listOf("Nobuo Uematsu", "Norihiko Hibino"), groups.map { it.name })
        assertEquals(listOf(2, 1), groups.map { it.trackCount })
        assertEquals("2 tracks", groups.first().subtitle)
    }

    @Test
    fun `case and whitespace do not split an artist in two`() {
        // Tags come from whatever wrote the file. "The Beatles" and "the beatles " are one band,
        // and two rows for them is the library reporting a difference the listener does not have.
        val groups = listOf(
            track("1", "Come Together", artist = "The Beatles"),
            track("2", "Something", artist = " the beatles "),
        ).artistGroups()
        assertEquals(1, groups.size)
        assertEquals("The Beatles", groups.single().name)   // the first spelling wins
        assertEquals(2, groups.single().trackCount)
    }

    @Test
    fun `a missing tag, a blank one and an empty one are the same bucket, and it sorts last`() {
        val groups = listOf(
            track("1", "Untitled"),
            track("2", "Untitled II", artist = ""),
            track("3", "Untitled III", artist = "   "),
            track("4", "Zoo Station", artist = "U2"),
        ).artistGroups()
        assertEquals(listOf("U2", "Unknown Artist"), groups.map { it.name })
        assertEquals(3, groups.last().trackCount)
    }

    @Test
    fun `an album keeps its compilation together and says so`() {
        // Grouping on album AND artist would turn one compilation into one row per artist. The
        // subtitle is what makes the merge legible.
        val groups = listOf(
            track("1", "Song A", artist = "Artist A", album = "Now That's What I Call Music"),
            track("2", "Song B", artist = "Artist B", album = "Now That's What I Call Music"),
            track("3", "Song C", artist = "Solo", album = "Just Mine"),
        ).albumGroups()
        assertEquals(listOf("Just Mine", "Now That's What I Call Music"), groups.map { it.name })
        assertEquals("Solo  ·  1 track", groups.first().subtitle)
        assertEquals("Various Artists  ·  2 tracks", groups.last().subtitle)
    }

    @Test
    fun `a group carries the first cover any of its tracks has`() {
        // The row is the only thing standing for the album, so it borrows art from whichever
        // track was tagged with it rather than showing a placeholder because track one was not.
        val groups = listOf(
            track("1", "Intro", album = "Kid A"),
            track("2", "Idioteque", album = "Kid A", artUri = "file:///art/kida.png"),
        ).albumGroups()
        assertEquals("file:///art/kida.png", groups.single().artUri)
    }

    @Test
    fun `the group key is what the drill-in filters by, not the display name`() {
        // The browser re-reads every track and keeps the ones matching the key. If the key were
        // the display name, the filter would miss every differently-cased copy of the tag.
        val group = listOf(
            track("1", "Come Together", artist = "The Beatles"),
            track("2", "Something", artist = "the beatles"),
        ).artistGroups().single()
        assertEquals("the beatles", group.key)
        val tracks = listOf(
            track("1", "Come Together", artist = "The Beatles"),
            track("2", "Something", artist = "the beatles"),
            track("3", "Kashmir", artist = "Led Zeppelin"),
        ).filter { it.artist.musicGroupKey() == group.key }
        assertEquals(listOf("1", "2"), tracks.map { it.id })
    }

    @Test
    fun `no tracks means no groups at all`() {
        assertEquals(emptyList<MusicGroup>(), emptyList<MusicTrack>().artistGroups())
        assertEquals(emptyList<MusicGroup>(), emptyList<MusicTrack>().albumGroups())
    }
}
