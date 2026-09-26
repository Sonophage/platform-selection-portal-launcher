package com.psplauncher.core.domain.repository

import com.psplauncher.core.domain.model.Video
import com.psplauncher.core.domain.model.VideoLibrary
import com.psplauncher.core.domain.model.VideoPlaylist
import kotlinx.coroutines.flow.Flow

interface VideoRepository {
    fun observeLibraries(): Flow<List<VideoLibrary>>
    fun observeEnabledLibraries(): Flow<List<VideoLibrary>>
    suspend fun getLibraries(): List<VideoLibrary>
    suspend fun getLibrary(id: String): VideoLibrary?
    suspend fun addLibrary(displayName: String, treeUri: String, scanRecursively: Boolean = true): VideoLibrary
    suspend fun renameLibrary(id: String, displayName: String)
    suspend fun setLibraryEnabled(id: String, enabled: Boolean)
    suspend fun setLibraryArtwork(id: String, artworkUri: String?)

    suspend fun removeLibrary(id: String)

    fun observeAllVideos(): Flow<List<Video>>
    fun observeVideosByLibrary(libraryId: String): Flow<List<Video>>
    suspend fun getVideo(id: String): Video?
    suspend fun getVideosForLibrary(libraryId: String): List<Video>

    suspend fun replaceVideosForLibrary(libraryId: String, videos: List<Video>, scannedAt: Long)

    suspend fun setResumePosition(id: String, positionMs: Long, watchedAt: Long)
    suspend fun clearResumePosition(id: String)
    suspend fun setCustomTitle(id: String, title: String?)
    suspend fun setCustomThumbnail(id: String, uri: String?)
    suspend fun removeVideo(id: String)

    fun observeFavorites(): Flow<List<Video>>
    fun observeRecentlyWatched(limit: Int = 30): Flow<List<Video>>
    suspend fun setFavorite(id: String, favorite: Boolean)

    suspend fun clearLastWatched(id: String)

    suspend fun getAllVideos(): List<Video>

    suspend fun setPosterUri(id: String, posterUri: String?)

    fun observePlaylists(): Flow<List<VideoPlaylist>>
    fun observePlaylistVideos(playlistId: Long): Flow<List<Video>>

    suspend fun getPlaylistIdsForVideo(videoId: String): List<Long>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(id: Long, name: String)
    suspend fun deletePlaylist(id: Long)
    suspend fun addVideoToPlaylist(playlistId: Long, videoId: String)
    suspend fun removeVideoFromPlaylist(playlistId: Long, videoId: String)

    suspend fun toggleVideoInPlaylist(playlistId: Long, videoId: String): Boolean

    fun observeDefaultVideoPlayer(): Flow<String?>
    suspend fun getDefaultVideoPlayer(): String?
    suspend fun setDefaultVideoPlayer(value: String?)

    fun observeNewestArtUris(limit: Int): Flow<List<String>>
}
