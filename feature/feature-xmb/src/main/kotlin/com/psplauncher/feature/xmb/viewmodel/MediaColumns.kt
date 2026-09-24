package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.ADD_MENU_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.ALL_BOOKS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.ALL_MUSIC_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.ALL_PHOTOS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.ALL_VIDEOS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.BOOK_SERIES_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.BOOK_SHELVES_ITEM_ID
import com.psplauncher.core.domain.model.MusicTrack
import com.psplauncher.core.domain.model.primaryArtist
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.CAMERA_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.MEMORY_CARD_ASSET_URI
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.MUSIC_ALBUMS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.MUSIC_ARTISTS_ITEM_ID
import com.psplauncher.core.domain.model.Video
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.NOW_PLAYING_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.OPEN_READER_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.PHOTO_ALBUMS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.PLAYLISTS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.VIDEO_COLLECTIONS_ITEM_ID
import com.psplauncher.feature.xmb.viewmodel.XMBViewModel.Companion.VIDEO_LIBRARIES_ITEM_ID

/**
 * What each media column shows at its root, as pure functions of the state.
 *
 * The same move as ContextMenus.kt, for the same reason: these decide the shape of Music, Video,
 * Photo and Library, they are about to be restructured, and inside a 9,000-line ViewModel there
 * was no way to assert what a column contains. Moving them first means the restructure lands
 * somewhere a test can see it.
 *
 * Extensions on XMBUiState because that is all they read — every `_uiState.value.x` became `x`.
 * Anything that needs a repository stays in the ViewModel.
 */

// Music root: the static items (Now Playing, when something is playing; Playlist; Music Apps)
// followed by the single "All Music" memory-card item. The root folder is managed in Settings →
// Music; a getting-started "Add Music Folder" row shows until a root has been added and scanned
// (keyed off the scan completing, not the track count), then drops away.
/**
 * The Music root's own sections, without the app rows.
 *
 * Separate from [musicRootItems] because the flyout's sibling column wants only the drillable
 * sections, while the published list also carries the installed music apps. Publishing this
 * one by mistake would silently drop the apps, which is why it is named for what it is.
 */
/**
 * Give each row in a media column its own four covers out of the column's pool.
 *
 * Applied at the END of a column's builder rather than at each `add`, so the offset is simply the
 * row's position and nobody has to keep a running index correct while editing the list.
 *
 * A ROW THAT IS ONE THING GETS NO GRID. The grid is art of what is INSIDE a row, and a playing
 * track or a resumed video has nothing inside it — it is the thing. Those rows wear their own
 * picture: the album cover, the video's thumbnail.
 *
 * Decided by TYPE, not by whether the row happens to have art. The first attempt excluded rows
 * with a coverUri, which is the same answer almost always and the wrong one exactly when it
 * matters: a video with no thumbnail yet would have been handed four unrelated thumbnails, and a
 * row about one film showing four other films is the failure this rule exists to prevent — at its
 * worst on the row least able to speak for itself.
 *
 * They do not take a slot either: the offset counts only the rows the grid will actually fill, so
 * a resume row appearing does not shift every grid below it onto somebody else's covers.
 */
private fun List<XMBItem>.withColumnCovers(pool: List<String>): List<XMBItem> {
    if (pool.isEmpty()) return this
    var slot = 0
    return map { item ->
        if (item.type in SINGLE_MEDIA_ITEM_TYPES) item
        else item.copy(insideCovers = pool.gridSliceAt(slot++))
    }
}

/** Rows that ARE a piece of media rather than a way into several. See [withColumnCovers]. */
private val SINGLE_MEDIA_ITEM_TYPES =
    setOf(XMBItemType.MUSIC_TRACK, XMBItemType.VIDEO_FILE, XMBItemType.LIBRARY_BOOK)

internal fun XMBUiState.musicRootSections(): List<XMBItem> {
    val folders = musicFolders
    val totalTracks = folders.sumOf { it.trackCount }
    return buildList {
        // Now Playing — only when a track is loaded; clicking returns to the active song.
        musicPlayback.track?.let { track ->
            // The scrubber rides the row that is already here. 6a puts the playing track in the
            // focus slot "with a scrubber under its meta", and this row has been the playing
            // track since Music had a column — so the tile, the title and the artist line all
            // stay exactly as they were and the bar is the only new thing.
            //
            // Guarded on a REAL duration. A stream or a file whose length has not been read yet
            // reports 0, and position-over-zero is either a divide by zero or a bar pinned full;
            // a row with no bar reads as a row with no bar, which is true.
            val total = musicPlayback.durationMs
            add(
                XMBItem(
                    id       = NOW_PLAYING_ITEM_ID,
                    title    = track.displayTitle,
                    subtitle = listOfNotNull("Now Playing", track.artist).joinToString("  ·  "),
                    coverUri = track.artUri,
                    progressFraction = if (total > 0) {
                        (musicPlayback.positionMs.toFloat() / total).coerceIn(0f, 1f)
                    } else null,
                    progressLabel = if (total > 0) {
                        formatDuration(musicPlayback.positionMs.toLong()) +
                            "  /  " + formatDuration(total.toLong())
                    } else null,
                    type     = XMBItemType.MUSIC_TRACK,   // renders the album-cover leading tile
                )
            )
        }
        // Songs leads: every scanned track, collapsed into one memory-card item (like All
        // Games), wearing the physical-media "_default.png" art rather than the blank console
        // fallback. Artists and Albums are the same tracks grouped, so they follow it rather
        // than the other way round -- the whole library first, then two ways of cutting it.
        add(
            XMBItem(
                id       = ALL_MUSIC_ITEM_ID,
                title    = "Songs",
                subtitle = countLabel(totalTracks, "track", "tracks"),
                coverUri = MEMORY_CARD_ASSET_URI,
                type     = XMBItemType.MEMORY_CARD,
            )
        )
        add(
            XMBItem(
                id       = MUSIC_ARTISTS_ITEM_ID,
                title    = "Artists",
                subtitle = "Browse by who made it",
                type     = XMBItemType.MUSIC_ARTISTS,
            )
        )
        add(
            XMBItem(
                id       = MUSIC_ALBUMS_ITEM_ID,
                title    = "Albums",
                subtitle = "Browse by release",
                type     = XMBItemType.MUSIC_ALBUMS,
            )
        )
        add(
            XMBItem(
                id       = PLAYLISTS_ITEM_ID,
                title    = "Playlists",
                subtitle = "Build and play your own track lists",
                type     = XMBItemType.PLAYLIST,
            )
        )
    }.withColumnCovers(mediaCovers.music)
}

// Video root: browse rows first (Collections, Video Libraries), then the Video Apps counterpart
// directly above the "Videos" memory card (second-to-bottom). The root folder is managed in
// Settings → Video; a getting-started "Add Videos" row shows until a root has been added and
// scanned (keyed off the scan completing, not the video count), then drops away.
/** The Video root's own sections, without the app rows (see [musicRootSections]). */
/**
 * One video as an XMB row.
 *
 * ONE mapper, used by the library lists and by the Video root's resume row. They were briefly two
 * — a hand-written row for resume beside toVideoItems — which is the shape where a row grows a
 * field in one place and not the other, and the two then disagree about what a video looks like.
 *
 * [lead] is the word that goes in front of the usual duration/resolution/size line: "Resume" for
 * the root's row, nothing for a plain listing.
 */
internal fun Video.toXmbRow(lead: String? = null): XMBItem = XMBItem(
    id       = "vid_$id",
    title    = displayTitle,
    subtitle = listOfNotNull(lead, videoRowSubtitle(durationMs, resolutionLabel, sizeBytes))
        .joinToString("  ·  "),
    type     = XMBItemType.VIDEO_FILE,
    mediaUri = uri,
    mimeType = mimeType,
    coverUri = effectiveThumbnailUri,
    progressFraction = videoProgressFraction(resumePositionMs, durationMs),
    progressLabel    = videoProgressLabel(resumePositionMs, durationMs),
)

internal fun XMBUiState.videoRootSections(): List<XMBItem> {
    val libraries = videoLibraries
    val totalVideos = libraries.sumOf { it.videoCount }
    return buildList {
        // Resume leads the column when there is something to resume — 6b's "Video · resume",
        // the counterpart of Music's Now Playing row. It is a whole row rather than a badge on
        // the library it lives in, because what you want after opening this column is usually the
        // thing you stopped watching, not the folder it came from.
        resumeVideo?.let { video ->
            add(video.toXmbRow(lead = "Resume"))
        }
        // Everything first, then the ways of narrowing it -- the same order Music reads in.
        add(
            XMBItem(
                id       = ALL_VIDEOS_ITEM_ID,
                title    = "Videos",
                subtitle = countLabel(totalVideos, "video", "videos"),
                coverUri = MEMORY_CARD_ASSET_URI,
                type     = XMBItemType.MEMORY_CARD,
            )
        )
        // The three curated views collapse into one "Collections" entry (drills into
        // Recently Watched / Favorites / Playlists) to keep the Video root uncluttered.
        add(
            XMBItem(
                id       = VIDEO_COLLECTIONS_ITEM_ID,
                title    = "Collections",
                subtitle = "Recently Watched, Favorites & Playlists",
                type     = XMBItemType.VIDEO_COLLECTIONS,
            )
        )
        add(
            XMBItem(
                id       = VIDEO_LIBRARIES_ITEM_ID,
                title    = "Video Libraries",
                subtitle = countLabel(libraries.size, "library", "libraries"),
                type     = XMBItemType.VIDEO_LIBRARY,
            )
        )
    }.withColumnCovers(mediaCovers.video)
}

// Photo root, PSP-style: Camera (when a camera app exists) and Albums first, then the Photo Apps
// counterpart directly above the "Photos" memory card (second-to-bottom), with the "Add Photo
// Library" row last — it disappears once a library has been scanned (further libraries are added
// from Settings → Photo).
/** The Photo root's own sections, without the app rows (see [musicRootSections]). */
// cameraAvailable is a PackageManager query the ViewModel caches, not state — so it comes in
// as an argument rather than being half-copied to here.
internal fun XMBUiState.photoRootSections(cameraAvailable: Boolean): List<XMBItem> {
    val libraries = photoLibraries
    val totalPhotos = libraries.sumOf { it.photoCount }
    return buildList {
        if (cameraAvailable) {
            add(
                XMBItem(
                    id       = CAMERA_ITEM_ID,
                    title    = "Camera",
                    subtitle = "Open the camera",
                    type     = XMBItemType.CAMERA,
                )
            )
        }
        // Camera stays at the top -- it is the one row here that is an action rather than a
        // view of the library. Then everything, then the way of narrowing it.
        add(
            XMBItem(
                id       = ALL_PHOTOS_ITEM_ID,
                title    = "Photos",
                subtitle = countLabel(totalPhotos, "photo", "photos"),
                coverUri = MEMORY_CARD_ASSET_URI,
                type     = XMBItemType.MEMORY_CARD,
            )
        )
        add(
            XMBItem(
                id       = PHOTO_ALBUMS_ITEM_ID,
                title    = "Albums",
                subtitle = countLabel(libraries.size, "album", "albums"),
                type     = XMBItemType.PHOTO_ALBUMS,
            )
        )
    }.withColumnCovers(mediaCovers.photo)
}

/** The Library root's own sections, without the app rows (see [musicRootSections]). */
internal fun XMBUiState.booksRootSections(): List<XMBItem> {
    val shelves = bookLibraries
    val totalBooks = shelves.sumOf { it.bookCount }
    val reader = defaultReader
    return buildList {
        // CONTINUE READING leads, when there is something to continue — the Books column's
        // counterpart to Now Playing and Resume.
        //
        // No page numbers. 6c draws "Vol. 4 · page 62 of 190" and PFP cannot know the 62: a book
        // opens in somebody else's reader, which never reports back. The Default Reader picker
        // does not even offer PSPLauncher, because there is no reader to offer. What IS knowable
        // is WHEN you last opened it, which is what this says — and that much only exists because
        // markBookOpened fires after the reader accepts the hand-off.
        continueBook?.let { book ->
            add(
                XMBItem(
                    id       = "book_${book.id}",
                    title    = book.displayTitle,
                    subtitle = listOfNotNull(
                        "Continue reading",
                        book.lastOpenedAt?.let { relativeDateTime(it) },
                    ).joinToString("  ·  "),
                    coverUri = book.coverUri,
                    type     = XMBItemType.LIBRARY_BOOK,
                )
            )
        }
        // The reader, first, so the app you read in is one press away whether or not you are
        // opening something from the library. Hidden when no reader is set, since there is
        // nothing to open: the picker lives in Settings.
        if (reader != null) {
            add(
                XMBItem(
                    id       = OPEN_READER_ITEM_ID,
                    title    = defaultReaderLabel ?: "Open Reader",
                    subtitle = "Open your reader",
                    type     = XMBItemType.LIBRARY_READER,
                )
            )
        }
        // Only worth a row once there is a choice to make, by the same rule as Series below.
        // With a single shelf the row opens a list of one whose only entry holds every book
        // the Books row already holds, so it is two extra presses to reach the same place --
        // and it reads as a distinction the library does not actually have.
        if (shelves.size > 1) {
            add(
                XMBItem(
                    id       = BOOK_SHELVES_ITEM_ID,
                    title    = "Shelves",
                    subtitle = countLabel(shelves.size, "shelf", "shelves"),
                    type     = XMBItemType.LIBRARY_SHELVES,
                )
            )
        }
        // Only worth a row once something declares a series. A library of standalones would
        // otherwise carry a row that opens an empty list.
        val series = bookSeries
        if (series.isNotEmpty()) {
            add(
                XMBItem(
                    id       = BOOK_SERIES_ITEM_ID,
                    title    = "Series",
                    subtitle = countLabel(series.size, "series", "series"),
                    type     = XMBItemType.LIBRARY_SERIES,
                )
            )
        }
        add(
            XMBItem(
                id       = ALL_BOOKS_ITEM_ID,
                title    = "Books",
                subtitle = countLabel(totalBooks, "book", "books"),
                coverUri = MEMORY_CARD_ASSET_URI,
                type     = XMBItemType.MEMORY_CARD,
            )
        )
    }.withColumnCovers(mediaCovers.books)
}

/**
 * A column ends in ONE Add row.
 *
 * A media column can offer two of them at once -- point the library at a folder, and pick
 * apps to show -- and two adjacent rows both starting with "Add" is a menu pretending to be a
 * list. With more than one, they collapse into a single "Add" row that opens the rest as a
 * submenu, which is one more press only for the case that was ambiguous anyway. With one,
 * that row is shown as itself: wrapping a single choice in a menu would be pure ceremony.
 */
internal fun collapseAddRows(rows: List<XMBItem>): List<XMBItem> = when {
    rows.size <= 1 -> rows
    else -> listOf(
        XMBItem(
            id       = ADD_MENU_ITEM_ID,
            title    = "Add",
            subtitle = rows.joinToString("  ·  ") { it.title.removePrefix("Add ") },
            type     = XMBItemType.ADD_ACTION,
        )
    )
}

// ── Artists and Albums ────────────────────────────────────────────────────────

/**
 * An artist or an album, as one row.
 *
 * [key] is what the tracks were grouped on -- trimmed and lowercased -- and is what the drill-in
 * filters by. [name] is what the row says, taken from the first track in the group, so "The
 * Beatles" and "the beatles" are one artist spelled the way the first file spells it.
 */
data class MusicGroup(
    val key: String,
    val name: String,
    val subtitle: String,
    val trackCount: Int,
    val artUri: String?,
)

/**
 * The grouping value for a tag, with every way of saying "absent" folded into one bucket.
 *
 * A missing tag, an empty one and one that is only spaces are the same thing to a listener, and
 * three separate "Unknown Artist" rows is what happens if they are not.
 */
internal fun String?.musicGroupKey(): String = this?.trim()?.lowercase().orEmpty()

/**
 * What separates two acts in a credit line, everywhere in this file.
 *
 * Comma-space and nothing else. " & " is not here on purpose: it is inside act names far more
 * often than it is between them ("Earth, Wind & Fire", "Hall & Oates", "Simon & Garfunkel"), so
 * splitting on it would invent artists that do not exist.
 */
private const val CreditSeparator = ", "

/**
 * Every credit line in this library that names exactly one act, lowercased.
 *
 * This is the EVIDENCE a split is allowed to use. A comma in a credit line means one of two
 * completely different things -- two acts, or one act whose name has a comma in it -- and the tag
 * itself cannot tell them apart. What can is the rest of the library: if "Kendrick Lamar" turns
 * up on its own somewhere, then "Kendrick Lamar, SZA" is two acts. If neither "Tyler" nor "The
 * Creator" ever turns up alone, "Tyler, The Creator" is one.
 *
 * A credit line that itself contains the separator is still in here as its whole self, which is
 * what lets "Earth, Wind & Fire" attest to itself without attesting to "Earth".
 */
internal fun List<MusicTrack>.soloCredits(): Set<String> =
    mapNotNullTo(mutableSetOf()) { it.primaryArtist.musicGroupKey().ifEmpty { null } }

/**
 * The acts one track is filed under, given what [soloCredits] the library has seen.
 *
 * Always at least one entry, and the empty string is the unknown bucket -- a track that vanished
 * from the Artists list because its credit could not be parsed would be a track the listener can
 * no longer find.
 *
 * A fragment the library has never seen alone is NOT dropped, and it is not left standing on its
 * own either: consecutive unattested fragments rejoin into one name. That single rule is what
 * makes all three cases come out right, and each of the simpler rules gets one of them wrong:
 *
 *   "Kendrick Lamar, SZA"              both seen alone      -> two acts
 *   "Andrew Lloyd Webber, Lana Del Rey" one seen alone      -> two acts, and Webber keeps a row
 *                                                              he would lose under "keep only
 *                                                              what you can prove"
 *   "Tyler, The Creator, Kali Uchis"   only the last alone  -> "Tyler, The Creator" and "Kali
 *                                                              Uchis", because the two unproven
 *                                                              fragments are ADJACENT and so are
 *                                                              read as the one name they are
 *
 * Splitting on any evidence at all would have invented a "Tyler" in that third line; requiring
 * evidence for every fragment would have left the second one a combination row, which on a real
 * library is most of them.
 */
internal fun MusicTrack.actsUnder(soloCredits: Set<String>): List<String> {
    val credit = primaryArtist?.trim()?.ifBlank { null } ?: return listOf("")
    val parts = credit.split(CreditSeparator).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return listOf(credit)

    val acts = mutableListOf<String>()
    val pending = mutableListOf<String>()
    fun flush() {
        if (pending.isNotEmpty()) { acts += pending.joinToString(CreditSeparator); pending.clear() }
    }
    parts.forEach { part ->
        if (part.musicGroupKey() in soloCredits) { flush(); acts += part } else pending += part
    }
    flush()
    return acts
}

/**
 * Every artist in a set of tracks, alphabetical, each carrying the first cover it can find.
 *
 * Grouped on [primaryArtist], not on `artist`. The `artist` tag holds whatever the file was
 * written with, which across a real library is the whole credit line -- so grouping on it listed
 * credit COMBINATIONS, with one performer in a dozen rows and no row for the performer alone.
 * The album artist names one act; it falls back to the credit line for a file that carries none,
 * which is right for a single and no worse than before for anything else.
 *
 * And where the fallback still leaves a joint credit, [actsUnder] splits it on the library's own
 * evidence, so one track can appear under several artists. That is why the counts here are of
 * MEMBERSHIPS rather than of tracks: a duet is one track and two rows, and it is a track in each.
 */
internal fun List<MusicTrack>.artistGroups(): List<MusicGroup> {
    val solo = soloCredits()
    return musicGroups({ it.actsUnder(solo) }, "Unknown Artist") { tracks ->
        countLabel(tracks.size, "track", "tracks")
    }
}

/**
 * The tracks one artist row stands for. The inverse of [artistGroups], and it has to stay so.
 *
 * Here rather than in the browser because the row and the drill-in are a pair that must agree:
 * the browser used to filter on `primaryArtist.musicGroupKey() == key` itself, which was the same
 * answer only for as long as a row meant exactly one whole credit line. The moment a line could
 * split, that copy would have opened every joint artist's row onto nothing.
 */
internal fun List<MusicTrack>.tracksByArtistKey(key: String): List<MusicTrack> {
    val solo = soloCredits()
    return filter { track -> track.actsUnder(solo).any { it.musicGroupKey() == key } }
}

/**
 * Every album in a set of tracks, alphabetical, subtitled with who is on it.
 *
 * Grouped on the album name alone, not on name-and-artist. A compilation is one album by a dozen
 * artists, and keying on both would shatter it into a dozen rows of one track each; the cost is
 * that two different records both called "Greatest Hits" merge, which the "Various Artists"
 * subtitle at least makes visible.
 */
internal fun List<MusicTrack>.albumGroups(): List<MusicGroup> =
    musicGroups({ listOf(it.album) }, "Unknown Album") { tracks ->
        val artists = tracks.mapNotNull { it.primaryArtist?.trim()?.ifBlank { null } }.distinct()
        listOfNotNull(
            when (artists.size) {
                0    -> null
                1    -> artists.single()
                else -> "Various Artists"
            },
            countLabel(tracks.size, "track", "tracks"),
        ).joinToString("  ·  ")
    }

/**
 * The one shape every music group row is built in.
 *
 * [tag] returns a LIST because an artist credit can name several acts and an album name cannot;
 * rather than two builders that drift apart, the single-valued case passes a list of one.
 */
private fun List<MusicTrack>.musicGroups(
    tag: (MusicTrack) -> List<String?>,
    unknownName: String,
    subtitle: (List<MusicTrack>) -> String,
): List<MusicGroup> =
    flatMap { track -> tag(track).map { it to track } }
        .groupBy { (value, _) -> value.musicGroupKey() }
        .map { (key, entries) ->
            val tracks = entries.map { it.second }
            MusicGroup(
                key = key,
                name = entries.firstNotNullOfOrNull { it.first?.trim()?.ifBlank { null } } ?: unknownName,
                subtitle = subtitle(tracks),
                trackCount = tracks.size,
                artUri = tracks.firstNotNullOfOrNull { it.artUri },
            )
        }
        // The unknown bucket sorts last whatever it is called: it is not a name, and an "Unknown
        // Artist" row landing between Tom Waits and Townes Van Zandt reads as one.
        .sortedWith(compareBy({ it.key.isEmpty() }, { it.name.lowercase() }))

// ── Music rows ────────────────────────────────────────────────────────────────

/** One row per track, as the Music column draws them. */
internal fun List<MusicTrack>.toMusicItems(): List<XMBItem> = map { track ->
    XMBItem(
        id            = "mt_${track.id}",
        title         = track.displayTitle,
        subtitle      = musicRowSubtitle(track.artist, track.album, track.durationMs),
        type          = XMBItemType.MUSIC_TRACK,
        mediaUri      = track.uri,
        mimeType      = track.mimeType,
        coverUri      = track.artUri,
        musicFolderId = track.folderId,
        musicGroupKey = track.album.musicGroupKey().ifEmpty { null },
    )
}

/**
 * The Recent shelf's music rows: a RUN of tracks from one album becomes one album row.
 *
 * An evening with one record used to be the entire shelf -- twelve rows of the same cover, with
 * the game you played yesterday pushed off the end. The shelf answers "what was I doing", and
 * "this album" is the honest answer to an evening of it.
 *
 * Consecutive only, never global. The list is newest-first, so a run IS a listening session;
 * grouping every track of an album wherever it appeared would reorder the shelf by album rather
 * than by recency, which is the one thing this list is for. A run of one stays a track: an album
 * row standing for a single track hides which track it was.
 *
 * Each pair is (recency stamp, row) to match what mergeRecents takes. A collapsed run carries
 * the NEWEST stamp in it, which is the one that earned its place.
 */
internal fun List<MusicTrack>.recentMusicRows(): List<Pair<Long, XMBItem>> {
    val rows = mutableListOf<Pair<Long, XMBItem>>()
    var i = 0
    while (i < size) {
        val key = this[i].album.musicGroupKey()
        // A track with no album tag can only ever be itself: every untagged track shares the
        // empty key, and collapsing on it would merge unrelated songs into one "album".
        var end = i + 1
        if (key.isNotEmpty()) {
            while (end < size && this[end].album.musicGroupKey() == key) end++
        }
        val run = subList(i, end)
        rows += if (run.size == 1) {
            (run[0].lastPlayedAt ?: 0L) to run.toMusicItems().single()
        } else {
            val name = run.firstNotNullOfOrNull { it.album?.trim()?.ifBlank { null } } ?: "Album"
            run.maxOf { it.lastPlayedAt ?: 0L } to XMBItem(
                id            = "mg_alb_$key",
                title         = name,
                subtitle      = countLabel(run.size, "track", "tracks"),
                coverUri      = run.firstNotNullOfOrNull { it.artUri },
                musicGroupKey = key,
                type          = XMBItemType.MUSIC_GROUP,
            )
        }
        i = end
    }
    return rows
}
