package com.psplauncher.feature.artwork.importer

import android.content.Context
import android.net.Uri
import com.psplauncher.core.data.platform.PlatformFolderHintResolver
import com.psplauncher.core.data.saf.SafChild
import com.psplauncher.core.data.saf.isIgnoredDir
import com.psplauncher.core.data.saf.querySafChildren
import com.psplauncher.feature.artwork.portable.ArtworkPathResolver
import com.psplauncher.feature.artwork.store.ArtworkKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

interface ArtworkImportSource {
    val sourceId: String

    suspend fun detect(treeUri: Uri, folder: SafChild): DetectedImportSource?

    suspend fun enumerate(treeUri: Uri, source: DetectedImportSource): EnumerationResult
}

data class EnumerationResult(
    val candidates: List<ImportCandidate>,
    val unknownSystemFolders: List<String>,
)

@Singleton
class EsDeImportSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platformResolver: PlatformFolderHintResolver,
) : ArtworkImportSource {
    override val sourceId = "esde"

    override suspend fun detect(treeUri: Uri, folder: SafChild): DetectedImportSource? =
        withContext(Dispatchers.IO) {
            val direct = systemFolders(treeUri, folder.documentId)
            val chosen = if (direct.systems.isNotEmpty()) {
                folder.documentId to direct
            } else {
                context.contentResolver.querySafChildren(treeUri, folder.documentId)
                    .firstOrNull { it.isDirectory && it.name.equals("downloaded_media", ignoreCase = true) }
                    ?.let { it.documentId to systemFolders(treeUri, it.documentId) }
                    ?: (folder.documentId to direct)
            }
            val (systemsRoot, scan) = chosen
            if (scan.systems.isEmpty()) return@withContext null
            DetectedImportSource(
                sourceId = sourceId,
                label = folder.name,
                folderDocId = folder.documentId,
                systemsRootDocId = systemsRoot,
                systems = scan.systems,
                gamelistDocIds = findGamelists(treeUri, folder.documentId, systemsRoot, scan.systems),
            )
        }

    override suspend fun enumerate(treeUri: Uri, source: DetectedImportSource): EnumerationResult =
        withContext(Dispatchers.IO) {
            val candidates = mutableListOf<ImportCandidate>()
            val unknown = systemFolders(treeUri, source.systemsRootDocId).unknownDirs
            for (system in source.systems) {
                for (mediaDir in context.contentResolver.querySafChildren(treeUri, system.docId)) {
                    if (!mediaDir.isDirectory) continue
                    val kind = ArtworkPathResolver.kindForMediaDir(mediaDir.name)
                        ?.takeIf { it in ArtworkPathResolver.importedKinds } ?: continue
                    collectRecursively(treeUri, mediaDir.documentId, system.platformId, kind, candidates, depth = 0)
                }
            }
            Timber.i("ES-DE enumerate: ${candidates.size} candidates across ${source.systems.size} systems")
            EnumerationResult(candidates, unknown)
        }

    private fun findGamelists(
        treeUri: Uri,
        folderDocId: String,
        systemsRootDocId: String,
        systems: List<DetectedImportSource.SystemFolder>,
    ): Map<String, String> {
        val out = mutableMapOf<String, String>()
        val gamelistsDir = listOf(folderDocId, systemsRootDocId).distinct().firstNotNullOfOrNull { parent ->
            context.contentResolver.querySafChildren(treeUri, parent)
                .firstOrNull { it.isDirectory && it.name.equals("gamelists", ignoreCase = true) }
        }
        if (gamelistsDir != null) {
            for (systemDir in context.contentResolver.querySafChildren(treeUri, gamelistsDir.documentId)) {
                if (!systemDir.isDirectory) continue
                val platformId = platformResolver.detectFromFolderName(systemDir.name) ?: continue
                context.contentResolver.querySafChildren(treeUri, systemDir.documentId)
                    .firstOrNull { !it.isDirectory && it.name.equals("gamelist.xml", ignoreCase = true) }
                    ?.let { out[platformId] = it.documentId }
            }
        }

        for (system in systems) {
            if (system.platformId in out) continue
            context.contentResolver.querySafChildren(treeUri, system.docId)
                .firstOrNull { !it.isDirectory && it.name.equals("gamelist.xml", ignoreCase = true) }
                ?.let { out[system.platformId] = it.documentId }
        }
        if (out.isNotEmpty()) Timber.i("ES-DE gamelists found for ${out.keys}")
        return out
    }

    private data class SystemScan(
        val systems: List<DetectedImportSource.SystemFolder>,
        val unknownDirs: List<String>,
    )

    private fun systemFolders(treeUri: Uri, parentDocId: String): SystemScan {
        val systems = mutableListOf<DetectedImportSource.SystemFolder>()
        val unknown = mutableListOf<String>()
        for (child in context.contentResolver.querySafChildren(treeUri, parentDocId)) {
            if (!child.isDirectory || child.isIgnoredDir()) continue
            val hasMediaDirs = context.contentResolver.querySafChildren(treeUri, child.documentId)
                .any { it.isDirectory && ArtworkPathResolver.isMediaDirName(it.name) }
            if (!hasMediaDirs) continue
            val platformId = platformResolver.detectFromFolderName(child.name)
            if (platformId != null) {
                systems += DetectedImportSource.SystemFolder(platformId, child.documentId, child.name)
            } else {
                unknown += child.name
            }
        }
        return SystemScan(systems, unknown)
    }

    private suspend fun collectRecursively(
        treeUri: Uri,
        dirDocId: String,
        platformId: String,
        kind: ArtworkKind,
        out: MutableList<ImportCandidate>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) return
        coroutineContext.ensureActive()
        for (child in context.contentResolver.querySafChildren(treeUri, dirDocId)) {
            when {
                child.isIgnoredDir() -> continue
                child.isDirectory ->
                    collectRecursively(treeUri, child.documentId, platformId, kind, out, depth + 1)
                isAcceptedName(kind, child.name) -> out += ImportCandidate(
                    platformId = platformId,
                    kind = kind.name,
                    documentId = child.documentId,
                    displayName = child.name,
                    sizeBytes = child.sizeBytes ?: 0L,
                )
            }
        }
    }

    private fun isAcceptedName(kind: ArtworkKind, name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (kind) {
            ArtworkKind.MANUAL -> ext == "pdf"

            ArtworkKind.VIDEO -> ext == "mp4" || ext == "m4v" || ext == "webm" || ext == "mkv"
            else -> ext == "png" || ext == "jpg" || ext == "jpeg" || ext == "webp"
        }
    }

    companion object {
        private const val MAX_DEPTH = 6
    }
}
