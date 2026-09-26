package com.psplauncher.feature.library.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.psplauncher.core.data.repository.Vita3KLibrary
import com.psplauncher.core.data.saf.SafChild
import com.psplauncher.core.data.saf.querySafChildren
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameContentType
import com.psplauncher.core.domain.repository.GameRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VitaGameScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vita3KLibrary: Vita3KLibrary,
    private val gameRepository: GameRepository,
) {
    data class VitaScanResult(val added: Int, val updated: Int, val found: Int, val message: String)

    suspend fun scan(): VitaScanResult = withContext(Dispatchers.IO) {
        val treeUri = vita3KLibrary.ux0TreeUri()?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: return@withContext VitaScanResult(0, 0, 0, "Set your Vita3K data folder (ux0) first.")

        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return@withContext VitaScanResult(0, 0, 0, "That Vita3K folder link is invalid — re-grant it.")

        val ux0DocId = resolveUx0Base(treeUri, rootDocId)
            ?: return@withContext VitaScanResult(0, 0, 0, "Couldn't find ux0/app in the granted folder — pick your Vita3K ux0 folder (or the folder that contains it).")
        val appDocId = findChildDir(treeUri, ux0DocId, "app")
            ?: return@withContext VitaScanResult(0, 0, 0, "No app/ folder under ux0 — check the granted folder.")

        val titleFolders = context.contentResolver.querySafChildren(treeUri, appDocId)
            .filter { it.isDirectory && looksLikeTitleId(it.name) }

        val existingByToken = gameRepository.getByPlatform(PSVITA_PLATFORM_ID)
            .filter { it.launchToken != null }
            .associateBy { it.launchToken }

        var added = 0
        var updated = 0
        for (folder in titleFolders) {
            val titleId = folder.name
            val sceSysId = findChildDir(treeUri, folder.documentId, "sce_sys") ?: continue
            val sceSysChildren = context.contentResolver.querySafChildren(treeUri, sceSysId)
            val title = sceSysChildren.fileUri("param.sfo")
                ?.let { readBytes(it, PARAM_SFO_MAX) }
                ?.let(ParamSfo::title)
                ?: titleId
            val iconUri = sceSysChildren.fileUri("icon0.png")?.toString()

            val existing = existingByToken[titleId]
            val gameId = when {
                existing == null -> {
                    added++
                    gameRepository.upsert(
                        Game(
                            title         = title,
                            platformId    = PSVITA_PLATFORM_ID,
                            launchToken   = titleId,
                            isManualEntry = true,
                            contentType   = GameContentType.GAME,
                            iconUri       = iconUri,
                        ),
                    )
                }
                existing.title != title || existing.iconUri != iconUri -> {
                    updated++
                    gameRepository.upsert(existing.copy(title = title, iconUri = iconUri ?: existing.iconUri))
                }
                else -> existing.id
            }
        }

        val message = when {
            titleFolders.isEmpty() -> "No installed Vita games found under ux0/app. Install a game in Vita3K first."
            added == 0 && updated == 0 -> "No new Vita games — ${titleFolders.size} already in your library."
            else -> "Imported $added Vita game(s)" + (if (updated > 0) ", updated $updated" else "") + "."
        }
        Timber.i("Vita scan — ${titleFolders.size} installed, $added added, $updated updated")
        VitaScanResult(added, updated, titleFolders.size, message)
    }

    private fun resolveUx0Base(treeUri: Uri, rootDocId: String): String? {
        if (findChildDir(treeUri, rootDocId, "app") != null) return rootDocId
        val ux0 = findChildDir(treeUri, rootDocId, "ux0") ?: return null
        return if (findChildDir(treeUri, ux0, "app") != null) ux0 else null
    }

    private fun findChildDir(treeUri: Uri, parentDocId: String, name: String): String? =
        context.contentResolver.querySafChildren(treeUri, parentDocId)
            .firstOrNull { it.isDirectory && it.name.equals(name, ignoreCase = true) }
            ?.documentId

    private fun List<SafChild>.fileUri(name: String): Uri? =
        firstOrNull { !it.isDirectory && it.name.equals(name, ignoreCase = true) }?.uri

    private fun readBytes(uri: Uri, maxBytes: Int): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            var remaining = maxBytes
            while (remaining > 0) {
                val read = input.read(buf, 0, minOf(buf.size, remaining))
                if (read == -1) break
                out.write(buf, 0, read)
                remaining -= read
            }
            out.toByteArray()
        }
    }.getOrNull()

    private fun looksLikeTitleId(name: String): Boolean =
        name.length == 9 && name.take(4).all { it in 'A'..'Z' } && name.drop(4).all { it.isDigit() }

    private companion object {
        const val PSVITA_PLATFORM_ID = "psvita"
        const val PARAM_SFO_MAX = 64 * 1024
    }
}
