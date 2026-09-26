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

private val KEY_BACKUP_FOLDER_TREE_URI = stringPreferencesKey("backup_folder_tree_uri")

@Singleton
class BackupFolderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val treeUri: Flow<String?> = context.pfpDataStore.data.map { it[KEY_BACKUP_FOLDER_TREE_URI] }

    suspend fun get(): String? = context.pfpDataStore.data.first()[KEY_BACKUP_FOLDER_TREE_URI]

    suspend fun set(treeUri: String?) {
        context.pfpDataStore.edit { prefs ->
            if (treeUri.isNullOrBlank()) prefs.remove(KEY_BACKUP_FOLDER_TREE_URI)
            else prefs[KEY_BACKUP_FOLDER_TREE_URI] = treeUri
        }
        Timber.i("Backup folder set: $treeUri")
    }

    fun persist(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Timber.w(it, "Could not persist backup folder permission for $uri") }
    }
}
