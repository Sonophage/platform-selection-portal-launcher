package com.psplauncher.core.data.repository

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.saf.querySafChildren
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private val KEY_SETUP_PROMPT_PENDING = booleanPreferencesKey("windows_library_setup_prompt")

sealed interface WindowsSetupState {
    data class Ready(val directory: String?) : WindowsSetupState

    data object NoRomRoot : WindowsSetupState

    data object FolderUnavailable : WindowsSetupState
}

@Singleton
class WindowsLibrarySetup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val romRoots: RomRootRepository,
    private val memoryCards: MemoryCardRepository,
) {
    internal interface FolderOps {
        fun findChildDir(treeUri: String, parentDocId: String, name: String): String?

        fun createChildDir(treeUri: String, parentDocId: String, name: String): String?
    }

    suspend fun ensure(): WindowsSetupState = withContext(Dispatchers.IO) { ensure(SafFolderOps()) }

    internal suspend fun ensure(ops: FolderOps): WindowsSetupState {
        val card = memoryCards.getById(PLATFORM_ID) ?: memoryCards.addCard(
            platformId      = PLATFORM_ID,
            displayName     = DISPLAY_NAME,
            romDirectory    = null,
            emulatorId      = null,
            extensions      = emptyList(),
            scanRecursively = false,
        )
        if (card.supportedExtensions.isNotEmpty()) memoryCards.setExtensions(PLATFORM_ID, emptyList())

        if (card.displayName == LEGACY_DISPLAY_NAME) memoryCards.rename(PLATFORM_ID, DISPLAY_NAME)

        card.treeUri?.takeIf { it.isNotBlank() }?.let { tree ->
            RomRootRepository.treeDocId(tree)?.let { docId -> ensureImportFolder(ops, tree, docId) }
            return WindowsSetupState.Ready(card.romDirectory)
        }

        val roots = romRoots.getAll()
        if (roots.isEmpty()) return WindowsSetupState.NoRomRoot

        val found = roots.firstNotNullOfOrNull { root -> windowsFolderUnder(ops, root) }
            ?: roots.firstNotNullOfOrNull { root ->
                RomRootRepository.treeDocId(root)
                    ?.let { rootDocId -> ops.createChildDir(root, rootDocId, WINDOWS_FOLDER) }
                    ?.let { root to it }
            }
            ?: return WindowsSetupState.FolderUnavailable

        val (rootUri, windowsDocId) = found
        ensureImportFolder(ops, rootUri, windowsDocId)
        val directory = RomRootRepository.docIdToRawPath(windowsDocId)
        if (directory != null && directory != card.romDirectory) {
            memoryCards.setRomDirectory(PLATFORM_ID, directory)
        }
        Timber.i("Windows library ready — dir=$directory")
        return WindowsSetupState.Ready(directory ?: card.romDirectory)
    }

    suspend fun windowsFolders(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        windowsFolders(SafFolderOps())
    }

    internal suspend fun windowsFolders(ops: FolderOps): List<Pair<String, String>> {
        memoryCards.getById(PLATFORM_ID)?.treeUri?.takeIf { it.isNotBlank() }?.let { tree ->
            return RomRootRepository.treeDocId(tree)?.let { listOf(tree to it) } ?: emptyList()
        }
        return romRoots.getAll().mapNotNull { root -> windowsFolderUnder(ops, root) }
    }

    private fun windowsFolderUnder(ops: FolderOps, rootTreeUri: String): Pair<String, String>? {
        val rootDocId = RomRootRepository.treeDocId(rootTreeUri) ?: return null
        return ops.findChildDir(rootTreeUri, rootDocId, WINDOWS_FOLDER)?.let { rootTreeUri to it }
    }

    private fun ensureImportFolder(ops: FolderOps, treeUri: String, windowsDocId: String) {
        if (ops.findChildDir(treeUri, windowsDocId, IMPORT_FOLDER) != null) return
        if (ops.createChildDir(treeUri, windowsDocId, IMPORT_FOLDER) == null) {
            Timber.w("Could not create the windows/$IMPORT_FOLDER drop-folder (read-only grant?)")
        }
    }

    suspend fun importFolders(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        importFolders(SafFolderOps())
    }

    internal suspend fun importFolders(ops: FolderOps): List<Pair<String, String>> =
        windowsFolders(ops).mapNotNull { (treeUri, windowsDocId) ->
            ops.findChildDir(treeUri, windowsDocId, IMPORT_FOLDER)?.let { treeUri to it }
        }

    suspend fun flagSetupPrompt() {
        context.pfpDataStore.edit { it[KEY_SETUP_PROMPT_PENDING] = true }
    }

    suspend fun consumeSetupPrompt(): Boolean {
        val pending = context.pfpDataStore.data.first()[KEY_SETUP_PROMPT_PENDING] == true
        if (pending) context.pfpDataStore.edit { it.remove(KEY_SETUP_PROMPT_PENDING) }
        return pending
    }

    private inner class SafFolderOps : FolderOps {
        override fun findChildDir(treeUri: String, parentDocId: String, name: String): String? {
            val tree = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return null
            return context.contentResolver.querySafChildren(tree, parentDocId)
                .firstOrNull { it.isDirectory && it.name.equals(name, ignoreCase = true) }
                ?.documentId
        }

        override fun createChildDir(treeUri: String, parentDocId: String, name: String): String? {
            val tree = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return null
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentDocId)
            return runCatching {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parentUri,
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    name,
                )
            }.getOrNull()?.let { DocumentsContract.getDocumentId(it) }
        }
    }

    companion object {
        const val PLATFORM_ID = com.psplauncher.core.domain.model.PlatformIds.WINDOWS
        const val DISPLAY_NAME = "Windows Memory Card"

        private const val LEGACY_DISPLAY_NAME = "Windows Games"
        const val WINDOWS_FOLDER = "windows"
        const val IMPORT_FOLDER = "import"
    }
}
