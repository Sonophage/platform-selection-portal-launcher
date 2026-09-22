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
        artUri: String? = null, albumArtist: String? = null,
    ) = MusicTrack(
        id = id, folderId = "f1", uri = "content://$id", displayName = "$id.mp3",
        title = title, artist = artist, album = album, artUri = artUri,
        albumArtist = albumArtist,
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
        ).tracksByArtistKey(group.key)
        assertEquals(listOf("1", "2"), tracks.map { it.id })
    }

    // ── Splitting a credit line, on the library's own evidence ────────────

    @Test
    fun `a joint credit splits on names the library has seen alone`() {
        // The pair this exists for. Both lines carry a comma; only one of them is two acts, and
        // nothing in the LINE says which. What says which is that "Kendrick Lamar" and "SZA" each
        // have a solo track in this library and no fragment of "Tyler, The Creator" has one.
        val groups = listOf(
            track("1", "All the Stars", artist = "Kendrick Lamar, SZA"),
            track("2", "HUMBLE.",      artist = "Kendrick Lamar"),
            track("3", "Good Days",    artist = "SZA"),
            track("4", "EARFQUAKE",    artist = "Tyler, The Creator"),
        ).artistGroups()

        assertEquals(listOf("Kendrick Lamar", "SZA", "Tyler, The Creator"), groups.map { it.name })
        // The duet counts in BOTH of its acts -- it is a track of each, not half a track of each.
        assertEquals(listOf(2, 2, 1), groups.map { it.trackCount })
    }

    @Test
    fun `a name the library has never seen alone still gets its own row`() {
        // Baby Keem has no solo track here, so nothing PROVES he is a separate act -- but the
        // name beside him is proven, which makes the comma a separator, which makes him one.
        // Dropping him instead would delete a real artist from the library's own index because
        // of what the library happens not to contain.
        val groups = listOf(
            track("1", "Family Ties", artist = "Kendrick Lamar, Baby Keem"),
            track("2", "HUMBLE.",     artist = "Kendrick Lamar"),
        ).artistGroups()
        assertEquals(listOf("Baby Keem", "Kendrick Lamar"), groups.map { it.name })
        assertEquals(listOf(1, 2), groups.map { it.trackCount })
    }

    @Test
    fun `unproven names next to each other are read as the one name they are`() {
        // The case that rules out splitting on any evidence at all. "Kali Uchis" is proven, so
        // the line does split -- but "Tyler" and "The Creator" are BOTH unproven and ADJACENT,
        // and a rapper called "Tyler" is not a thing this library has ever seen.
        val groups = listOf(
            track("1", "See You Again", artist = "Tyler, The Creator, Kali Uchis"),
            track("2", "Telepatia",     artist = "Kali Uchis"),
        ).artistGroups()
        assertEquals(listOf("Kali Uchis", "Tyler, The Creator"), groups.map { it.name })
    }

    @Test
    fun `an act whose own name has a comma survives when no part of it stands alone`() {
        // Under a plain comma split this band is two bands. What stops it is that neither half
        // has ever been seen on its own.
        val ordinary = listOf(
            track("1", "September", artist = "Earth, Wind & Fire"),
            track("2", "Solo",      artist = "Chic"),
        ).artistGroups()
        assertEquals(listOf("Chic", "Earth, Wind & Fire"), ordinary.map { it.name })

        // And the honest limit, recorded rather than left to be discovered: a library that really
        // does contain a separate act called "Earth" makes the comma look like a separator, and
        // the band comes apart. Nothing in a tag can tell these two libraries apart.
        val coincidence = listOf(
            track("1", "September", artist = "Earth, Wind & Fire"),
            track("2", "Solo",      artist = "Earth"),
        ).artistGroups()
        assertEquals(listOf("Earth", "Wind & Fire"), coincidence.map { it.name })
    }

    @Test
    fun `an ampersand is never a separator`() {
        // " & " sits inside act names far more often than between them. Splitting on it would
        // invent a "Garfunkel" nobody recorded anything as.
        val groups = listOf(
            track("1", "The Boxer", artist = "Simon & Garfunkel"),
            track("2", "Sound",     artist = "Simon"),
            track("3", "Angel",     artist = "Garfunkel"),
        ).artistGroups()
        assertEquals(listOf("Garfunkel", "Simon", "Simon & Garfunkel"), groups.map { it.name })
    }

    @Test
    fun `every artist row opens onto exactly the tracks it counted`() {
        // The Rule 13 guard. The row and the drill-in are two things that must agree, and before
        // this they were two separate expressions -- the browser filtered on the whole credit
        // line while the row could stand for a fragment of one. This fails the moment they drift.
        val library = listOf(
            track("1", "All the Stars", artist = "Kendrick Lamar, SZA"),
            track("2", "HUMBLE.",       artist = "Kendrick Lamar"),
            track("3", "Good Days",     artist = "SZA"),
            track("4", "EARFQUAKE",     artist = "Tyler, The Creator"),
            track("5", "Untitled",      artist = "   "),
        )
        library.artistGroups().forEach { group ->
            assertEquals(
                "row \"${group.name}\" counts ${group.trackCount} but opens onto a different set",
                group.trackCount,
                library.tracksByArtistKey(group.key).size,
            )
        }
    }

    @Test
    fun `no tracks means no groups at all`() {
        assertEquals(emptyList<MusicGroup>(), emptyList<MusicTrack>().artistGroups())
        assertEquals(emptyList<MusicGroup>(), emptyList<MusicTrack>().albumGroups())
    }

    // ── The Recent shelf's music rows ─────────────────────────────────────

    private fun played(
        id: String, title: String, album: String? = null, at: Long, artUri: String? = null,
    ) = MusicTrack(
        id = id, folderId = "f1", uri = "content://$id", displayName = "$id.mp3",
        title = title, album = album, artUri = artUri, lastPlayedAt = at,
    )

    @Test
    fun `a run of one album becomes one album row`() {
        // An evening with one record used to be the whole shelf.
        val rows = listOf(
            played("1", "Everything In Its Right Place", album = "Kid A", at = 900),
            played("2", "Kid A", album = "Kid A", at = 800),
            played("3", "The National Anthem", album = "Kid A", at = 700),
        ).recentMusicRows()
        assertEquals(1, rows.size)
        assertEquals("Kid A", rows.single().second.title)
        assertEquals("3 tracks", rows.single().second.subtitle)
        assertEquals(XMBItemType.MUSIC_GROUP, rows.single().second.type)
        // The newest stamp in the run: that is the one that earned its place on the shelf.
        assertEquals(900L, rows.single().first)
    }

    @Test
    fun `only CONSECUTIVE tracks collapse`() {
        // Grouping every track of an album wherever it appeared would order the shelf by album
        // instead of by recency, which is the one thing this list is for.
        val rows = listOf(
            played("1", "Idioteque", album = "Kid A", at = 900),
            played("2", "Song B", album = "Other", at = 800),
            played("3", "Optimistic", album = "Kid A", at = 700),
        ).recentMusicRows()
        assertEquals(listOf("Idioteque", "Song B", "Optimistic"), rows.map { it.second.title })
        assertTrue(rows.all { it.second.type == XMBItemType.MUSIC_TRACK })
    }

    @Test
    fun `a run of one stays a track`() {
        // An album row standing for a single track hides which track it was.
        val rows = listOf(played("1", "Idioteque", album = "Kid A", at = 900)).recentMusicRows()
        assertEquals("Idioteque", rows.single().second.title)
        assertEquals(XMBItemType.MUSIC_TRACK, rows.single().second.type)
    }

    @Test
    fun `untagged tracks never collapse into each other`() {
        // Every untagged track shares the empty album key. Collapsing on it would merge unrelated
        // songs into one row calling itself an album.
        val rows = listOf(
            played("1", "Untitled", at = 900),
            played("2", "Untitled II", album = "", at = 800),
            played("3", "Untitled III", album = "   ", at = 700),
        ).recentMusicRows()
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.second.type == XMBItemType.MUSIC_TRACK })
    }

    @Test
    fun `the album row carries the key the drill-in filters by, and a cover`() {
        val rows = listOf(
            played("1", "Intro", album = "Kid A", at = 900),
            played("2", "Idioteque", album = "Kid A", at = 800, artUri = "file:///art/kida.png"),
        ).recentMusicRows()
        assertEquals("kid a", rows.single().second.musicGroupKey)
        assertEquals("file:///art/kida.png", rows.single().second.coverUri)
    }

    // ── Video resume ──────────────────────────────────────────────────────

    @Test
    fun `an unstarted or unmeasurable video has no progress at all`() {
        // Null, not zero: the bar and the words both read null as "say nothing", and a zero would
        // draw an empty bar on every video that has never been opened.
        assertEquals(null, videoProgressFraction(0L, 60_000L))
        assertEquals(null, videoProgressFraction(30_000L, null))
        assertEquals(null, videoProgressFraction(30_000L, 0L))
        assertEquals(null, videoProgressLabel(0L, 60_000L))
    }

    @Test
    fun `a resume point past the end is a stale stamp, not a finished video`() {
        // It happens: the duration is re-probed smaller, or the file is replaced. Reporting 110%
        // would draw a bar wider than its track.
        assertEquals(null, videoProgressFraction(120_000L, 60_000L))
    }

    @Test
    fun `the words say what is left, not what is done`() {
        // What the next press costs you is the question a resume point answers; "62%" makes you
        // do the arithmetic to get there.
        assertEquals("30 min left", videoProgressLabel(30 * 60_000L, 60 * 60_000L))
        assertEquals("1 hr left", videoProgressLabel(60 * 60_000L, 120 * 60_000L))
        assertEquals("1 hr 30 min left", videoProgressLabel(30 * 60_000L, 120 * 60_000L))
        assertEquals("Almost finished", videoProgressLabel(119 * 60_000L + 59_000L, 120 * 60_000L))
    }

    // ── Album artist ──────────────────────────────────────────────────────

    @Test
    fun `artists group on the album artist, not the credit line`() {
        // The whole reason album_artist exists. These are real values off the device: the artist
        // tag is the full credit, so grouping on it gave a row per COMBINATION and no row for
        // Kendrick alone.
        val groups = listOf(
            track("1", "Bloody Waters", artist = "Ab-Soul, Anderson .Paak, James Blake",
                  albumArtist = "Kendrick Lamar", album = "Black Panther"),
            track("2", "King's Dead", artist = "Jay Rock, Kendrick Lamar, Future",
                  albumArtist = "Kendrick Lamar", album = "Black Panther"),
        ).artistGroups()
        assertEquals(listOf("Kendrick Lamar"), groups.map { it.name })
        assertEquals(2, groups.single().trackCount)
    }

    @Test
    fun `a file with no album artist keeps grouping by what it does have`() {
        // Every track scanned before album_artist existed has null there, and the column is NOT
        // backfilled. Falling back keeps that library exactly as it was rather than collapsing
        // all of it into one "Unknown Artist" row until a rescan.
        val groups = listOf(
            track("1", "Zoo Station", artist = "U2"),
            track("2", "One", artist = "U2"),
        ).artistGroups()
        assertEquals(listOf("U2"), groups.map { it.name })
        assertEquals(2, groups.single().trackCount)
    }

    @Test
    fun `a blank album artist is no album artist`() {
        // Tag writers leave empty strings behind. Treating "" as a name gives a nameless row that
        // sorts first and swallows every file written by that tool.
        val groups = listOf(track("1", "Song", artist = "Real Band", albumArtist = "   ")).artistGroups()
        assertEquals(listOf("Real Band"), groups.map { it.name })
    }

    @Test
    fun `an album is Various Artists only when its ACTS differ, not its credits`() {
        // A compilation is many acts. A record where every track credits a different guest is
        // still one act's record, and calling it Various Artists would be wrong about the thing
        // the row is standing for.
        val oneAct = listOf(
            track("1", "A", artist = "Kendrick Lamar, SZA", albumArtist = "Kendrick Lamar", album = "Black Panther"),
            track("2", "B", artist = "Kendrick Lamar, Future", albumArtist = "Kendrick Lamar", album = "Black Panther"),
        ).albumGroups()
        assertEquals("Kendrick Lamar  ·  2 tracks", oneAct.single().subtitle)

        val manyActs = listOf(
            track("1", "A", artist = "Artist A", albumArtist = "Artist A", album = "Now 42"),
            track("2", "B", artist = "Artist B", albumArtist = "Artist B", album = "Now 42"),
        ).albumGroups()
        assertEquals("Various Artists  ·  2 tracks", manyActs.single().subtitle)
    }
}
