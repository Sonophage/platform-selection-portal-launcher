package com.psplauncher.core.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
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

enum class MediaRootKind(internal val key: String) {
    MUSIC("music_root_tree_uris"),
    VIDEO("video_root_tree_uris"),
    PHOTO("photo_root_tree_uris"),
    BOOK("book_root_tree_uris"),
}

@Singleton
class MediaRootRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun roots(kind: MediaRootKind): Flow<List<String>> =
        context.pfpDataStore.data.map { readRoots(it, kind) }

    suspend fun getAll(kind: MediaRootKind): List<String> =
        readRoots(context.pfpDataStore.data.first(), kind)

    suspend fun add(kind: MediaRootKind, treeUri: String) {
        if (treeUri.isBlank()) return
        val next = LinkedHashSet(getAll(kind)).apply { add(treeUri) }.toList()
        writeRoots(kind, next)
        Timber.i("%s root added: %s (total %d)", kind.name, treeUri, next.size)
    }

    suspend fun remove(kind: MediaRootKind, treeUri: String) {
        val next = getAll(kind).filterNot { it == treeUri }
        writeRoots(kind, next)
        Timber.i("%s root removed: %s (total %d)", kind.name, treeUri, next.size)
    }

    suspend fun replace(kind: MediaRootKind, oldTreeUri: String, newTreeUri: String) {
        if (newTreeUri.isBlank()) return
        val current = getAll(kind)
        val next = if (oldTreeUri in current) {
            LinkedHashSet(current.map { if (it == oldTreeUri) newTreeUri else it })
        } else {
            LinkedHashSet(current).apply { add(newTreeUri) }
        }
        writeRoots(kind, next.toList())
    }

    fun persist(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Timber.w(it, "Could not persist media root permission for %s", uri) }
    }

    suspend fun clear(kind: MediaRootKind) {
        writeRoots(kind, emptyList())
    }

    private fun readRoots(prefs: Preferences, kind: MediaRootKind): List<String> =
        prefs[stringPreferencesKey(kind.key)]
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    private suspend fun writeRoots(kind: MediaRootKind, roots: List<String>) {
        context.pfpDataStore.edit { prefs ->
            if (roots.isEmpty()) prefs.remove(stringPreferencesKey(kind.key))
            else prefs[stringPreferencesKey(kind.key)] = roots.joinToString("\n")
        }
    }
}
