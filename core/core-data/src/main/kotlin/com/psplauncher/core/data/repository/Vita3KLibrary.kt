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

private val KEY_VITA3K_UX0_TREE_URI = stringPreferencesKey("vita3k_ux0_tree_uri")

@Singleton
class Vita3KLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val ux0TreeUriFlow: Flow<String?> = context.pfpDataStore.data.map { it[KEY_VITA3K_UX0_TREE_URI] }

    suspend fun ux0TreeUri(): String? = context.pfpDataStore.data.first()[KEY_VITA3K_UX0_TREE_URI]

    suspend fun setUx0Folder(treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Timber.w(it, "Could not persist Vita3K ux0 grant") }
        context.pfpDataStore.edit { it[KEY_VITA3K_UX0_TREE_URI] = treeUri.toString() }
        Timber.i("Vita3K ux0 folder set to: $treeUri")
    }

    suspend fun clear() {
        context.pfpDataStore.edit { it.remove(KEY_VITA3K_UX0_TREE_URI) }
    }
}
