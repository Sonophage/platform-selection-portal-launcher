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

private fun List<XMBItem>.withColumnCovers(pool: List<String>): List<XMBItem> {
    if (pool.isEmpty()) return this
    var slot = 0
    return map { item ->
        if (item.type in SINGLE_MEDIA_ITEM_TYPES) item
        else item.copy(insideCovers = pool.gridSliceAt(slot++))
    }
}

private val SINGLE_MEDIA_ITEM_TYPES =
    setOf(XMBItemType.MUSIC_TRACK, XMBItemType.VIDEO_FILE, XMBItemType.LIBRARY_BOOK)

internal fun XMBUiState.musicRootSections(): List<XMBItem> {
    val folders = musicFolders
    val totalTracks = folders.sumOf { it.trackCount }
    return buildList {
        musicPlayback.track?.let { track ->

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
                    type     = XMBItemType.MUSIC_TRACK,
                )
            )
        }

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
        resumeVideo?.let { video ->
            add(video.toXmbRow(lead = "Resume"))
        }

        add(
            XMBItem(
                id       = ALL_VIDEOS_ITEM_ID,
                title    = "Videos",
                subtitle = countLabel(totalVideos, "video", "videos"),
                coverUri = MEMORY_CARD_ASSET_URI,
                type     = XMBItemType.MEMORY_CARD,
            )
        )

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

internal fun XMBUiState.booksRootSections(): List<XMBItem> {
    val shelves = bookLibraries
    val totalBooks = shelves.sumOf { it.bookCount }
    val reader = defaultReader
    return buildList {
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

data class MusicGroup(
    val key: String,
    val name: String,
    val subtitle: String,
    val trackCount: Int,
    val artUri: String?,
)

internal fun String?.musicGroupKey(): String = this?.trim()?.lowercase().orEmpty()

private const val CreditSeparator = ", "

internal fun List<MusicTrack>.soloCredits(): Set<String> =
    mapNotNullTo(mutableSetOf()) { it.primaryArtist.musicGroupKey().ifEmpty { null } }

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

internal fun List<MusicTrack>.artistGroups(): List<MusicGroup> {
    val solo = soloCredits()
    return musicGroups({ it.actsUnder(solo) }, "Unknown Artist") { tracks ->
        countLabel(tracks.size, "track", "tracks")
    }
}

internal fun List<MusicTrack>.tracksByArtistKey(key: String): List<MusicTrack> {
    val solo = soloCredits()
    return filter { track -> track.actsUnder(solo).any { it.musicGroupKey() == key } }
}

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

        .sortedWith(compareBy({ it.key.isEmpty() }, { it.name.lowercase() }))

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

internal fun List<MusicTrack>.recentMusicRows(): List<Pair<Long, XMBItem>> {
    val rows = mutableListOf<Pair<Long, XMBItem>>()
    var i = 0
    while (i < size) {
        val key = this[i].album.musicGroupKey()

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
