package com.psplauncher.core.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.psplauncher.core.data.datastore.pfpDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_ARTWORK_FOLDER_TREE_URI = stringPreferencesKey("artwork_folder_tree_uri")

private val KEY_ARTWORK_STORAGE_MODE = stringPreferencesKey("artwork_storage_mode")

private val KEY_ARTWORK_LIBRARY_UUID = stringPreferencesKey("artwork_library_uuid")

enum class ArtworkStorageMode { INTERNAL, PORTABLE;
    companion object {
        fun fromName(name: String?): ArtworkStorageMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: INTERNAL
    }
}

@Singleton
class ArtworkFolderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val treeUri: Flow<String?> = context.pfpDataStore.data.map { it[KEY_ARTWORK_FOLDER_TREE_URI] }

    val storageMode: Flow<ArtworkStorageMode> =
        context.pfpDataStore.data.map { ArtworkStorageMode.fromName(it[KEY_ARTWORK_STORAGE_MODE]) }

    suspend fun getTreeUri(): String? =
        context.pfpDataStore.data.first()[KEY_ARTWORK_FOLDER_TREE_URI]

    suspend fun getStorageMode(): ArtworkStorageMode =
        ArtworkStorageMode.fromName(context.pfpDataStore.data.first()[KEY_ARTWORK_STORAGE_MODE])

    suspend fun getLibraryUuid(): String? =
        context.pfpDataStore.data.first()[KEY_ARTWORK_LIBRARY_UUID]

    suspend fun setTreeUri(treeUri: String?) {
        context.pfpDataStore.edit { prefs ->
            if (treeUri.isNullOrBlank()) prefs.remove(KEY_ARTWORK_FOLDER_TREE_URI)
            else prefs[KEY_ARTWORK_FOLDER_TREE_URI] = treeUri
        }
        Timber.i("Artwork folder set: $treeUri")
    }

    suspend fun setStorageMode(mode: ArtworkStorageMode) {
        context.pfpDataStore.edit { it[KEY_ARTWORK_STORAGE_MODE] = mode.name.lowercase() }
        Timber.i("Artwork storage mode: $mode")
    }

    suspend fun setLibraryUuid(uuid: String?) {
        context.pfpDataStore.edit { prefs ->
            if (uuid.isNullOrBlank()) prefs.remove(KEY_ARTWORK_LIBRARY_UUID)
            else prefs[KEY_ARTWORK_LIBRARY_UUID] = uuid
        }
    }

    fun persist(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Timber.w(it, "Could not persist artwork folder permission for $uri") }
    }

    suspend fun hasLiveGrant(): Boolean {
        val stored = getTreeUri() ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri.toString() == stored && it.isReadPermission && it.isWritePermission
        }
    }

    suspend fun forget() {
        getTreeUri()?.let { stored ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(stored),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }.onFailure { Timber.w(it, "Could not release artwork folder permission for $stored") }
        }
        context.pfpDataStore.edit { prefs ->
            prefs.remove(KEY_ARTWORK_FOLDER_TREE_URI)
            prefs.remove(KEY_ARTWORK_STORAGE_MODE)
            prefs.remove(KEY_ARTWORK_LIBRARY_UUID)
        }
        Timber.i("Artwork folder forgotten (grant released, files untouched)")
    }
}
