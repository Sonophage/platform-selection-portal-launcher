package com.psplauncher.core.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
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

private val KEY_ROM_ROOT_TREE_URIS = stringPreferencesKey("library_rom_root_tree_uris")
private val KEY_LEGACY_ROM_ROOT    = stringPreferencesKey("library_rom_root_tree_uri")

@Singleton
class RomRootRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val roots: Flow<List<String>> = context.pfpDataStore.data.map { readRoots(it) }

    suspend fun getAll(): List<String> = readRoots(context.pfpDataStore.data.first())

    suspend fun add(treeUri: String) {
        if (treeUri.isBlank()) return
        val next = LinkedHashSet(getAll()).apply { add(treeUri) }.toList()
        writeRoots(next)
        Timber.i("ROM root added: $treeUri (total ${next.size})")
    }

    suspend fun remove(treeUri: String) {
        val next = getAll().filterNot { it == treeUri }
        writeRoots(next)
        Timber.i("ROM root removed: $treeUri (total ${next.size})")
    }

    suspend fun replace(oldTreeUri: String, newTreeUri: String) {
        if (newTreeUri.isBlank()) return
        val current = getAll()
        val next = if (oldTreeUri in current) {
            LinkedHashSet(current.map { if (it == oldTreeUri) newTreeUri else it })
        } else {
            LinkedHashSet(current).apply { add(newTreeUri) }
        }
        writeRoots(next.toList())
    }

    fun persist(uri: Uri, writable: Boolean = false) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
            (if (writable) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
        }.onFailure { Timber.w(it, "Could not persist ROM root permission for $uri") }
    }

    private fun readRoots(prefs: Preferences): List<String> {
        val plural = prefs[KEY_ROM_ROOT_TREE_URIS]
            ?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }
            .orEmpty()
        if (plural.isNotEmpty()) return plural

        return prefs[KEY_LEGACY_ROM_ROOT]?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList()
    }

    private suspend fun writeRoots(roots: List<String>) {
        context.pfpDataStore.edit { prefs ->
            if (roots.isEmpty()) prefs.remove(KEY_ROM_ROOT_TREE_URIS)
            else prefs[KEY_ROM_ROOT_TREE_URIS] = roots.joinToString("\n")

            prefs.remove(KEY_LEGACY_ROM_ROOT)
        }
    }

    companion object {
        fun rawPathOfTree(treeUri: String): String? {
            val docId = treeDocId(treeUri) ?: return null
            return docIdToRawPath(docId)
        }

        fun treeDocId(treeUri: String): String? =
            runCatching { DocumentsContract.getTreeDocumentId(Uri.parse(treeUri)) }.getOrNull()

        fun childDocIdOf(treeUri: String, subfolderName: String): String? {
            val root = treeDocId(treeUri) ?: return null
            return "$root/$subfolderName"
        }

        fun docIdToRawPath(documentId: String): String? {
            val parts = documentId.split(":", limit = 2)
            if (parts.size != 2 || parts[1].isBlank()) return null
            val (volume, relative) = parts
            return if (volume.equals("primary", ignoreCase = true)) {
                "/storage/emulated/0/$relative"
            } else {
                "/storage/$volume/$relative"
            }
        }

        fun childDocIdFrom(rootDocId: String, rootRawPath: String, romDirectory: String): String? {
            val normRoot = rootRawPath.trimEnd('/')
            val normDir  = romDirectory.trimEnd('/')
            if (normDir != normRoot && !normDir.startsWith("$normRoot/")) return null
            val relative = normDir.removePrefix(normRoot).trim('/')
            return if (relative.isEmpty()) rootDocId else "$rootDocId/$relative"
        }
    }
}
