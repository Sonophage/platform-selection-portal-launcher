package com.psplauncher.core.domain.repository

import com.psplauncher.core.domain.model.Photo
import com.psplauncher.core.domain.model.PhotoLibrary
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {
    fun observeLibraries(): Flow<List<PhotoLibrary>>
    suspend fun getLibraries(): List<PhotoLibrary>
    suspend fun getLibrary(id: String): PhotoLibrary?

    suspend fun addLibrary(displayName: String, treeUri: String, scanRecursively: Boolean = true): PhotoLibrary
    suspend fun renameLibrary(id: String, displayName: String)

    suspend fun setLibraryScanRecursively(id: String, scanRecursively: Boolean)

    suspend fun setLibraryTreeUri(id: String, treeUri: String)

    suspend fun removeLibrary(id: String)

    fun observeAllPhotos(): Flow<List<Photo>>
    fun observePhotosByLibrary(libraryId: String): Flow<List<Photo>>
    suspend fun getPhoto(id: String): Photo?
    suspend fun getPhotosForLibrary(libraryId: String): List<Photo>

    suspend fun replacePhotosForLibrary(libraryId: String, photos: List<Photo>, scannedAt: Long)

    suspend fun removePhoto(id: String)

    fun observeNewestArtUris(limit: Int): Flow<List<String>>
}
