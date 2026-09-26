package com.psplauncher.core.domain.repository

import com.psplauncher.core.domain.model.MusicFolder
import com.psplauncher.core.domain.model.MusicTrack
import com.psplauncher.core.domain.model.Playlist
import kotlinx.coroutines.flow.Flow

interface MusicRepository {
    fun observeFolders(): Flow<List<MusicFolder>>
    fun observeEnabledFolders(): Flow<List<MusicFolder>>
    suspend fun getFolders(): List<MusicFolder>
    suspend fun getFolder(id: String): MusicFolder?
    suspend fun addFolder(displayName: String, treeUri: String): MusicFolder
    suspend fun renameFolder(id: String, displayName: String)
    suspend fun setFolderEnabled(id: String, enabled: Boolean)

    suspend fun removeFolder(id: String)

    fun observeAllTracks(): Flow<List<MusicTrack>>
    fun observeTracksByFolder(folderId: String): Flow<List<MusicTrack>>
    suspend fun getTrack(id: String): MusicTrack?

    suspend fun replaceTracksForFolder(folderId: String, tracks: List<MusicTrack>, scannedAt: Long)

    suspend fun markTrackPlayed(trackId: String, playedAt: Long)

    suspend fun clearTrackLastPlayed(trackId: String)
    fun observeRecentlyPlayedTracks(limit: Int): Flow<List<MusicTrack>>

    fun observeDefaultPlayerPackage(): Flow<String?>
    suspend fun getDefaultPlayerPackage(): String?
    suspend fun setDefaultPlayerPackage(packageName: String?)

    fun observePlaylists(): Flow<List<Playlist>>
    fun observePlaylistTracks(playlistId: Long): Flow<List<MusicTrack>>

    suspend fun getPlaylistIdsForTrack(trackId: String): List<Long>
    suspend fun createPlaylist(name: String): Long
    suspend fun renamePlaylist(id: Long, name: String)
    suspend fun deletePlaylist(id: Long)
    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String)
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String)

    suspend fun toggleTrackInPlaylist(playlistId: Long, trackId: String): Boolean

    fun observeNewestArtUris(limit: Int): Flow<List<String>>
}
