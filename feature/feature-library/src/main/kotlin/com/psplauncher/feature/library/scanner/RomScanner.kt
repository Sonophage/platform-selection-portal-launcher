package com.psplauncher.feature.library.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.psplauncher.core.data.platform.PlatformFolderHintResolver
import com.psplauncher.core.data.saf.querySafChildren
import com.psplauncher.core.domain.model.Game
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class ScanProgress(
    val currentFolder: String,
    val filesScanned: Int,
    val filesFound: Int,
    val totalEstimated: Int,
)

sealed class ScanResult {
    data class Progress(val progress: ScanProgress) : ScanResult()
    data class Complete(
        val newGames: List<Game>,
        val alreadyInLibrary: Int,
        val unmatched: List<UnmatchedRom>,
        val requiresUserAssignment: List<UnmatchedRom>,

        val presentRomPaths: Set<String>? = null,
    ) : ScanResult()
    data class Error(val message: String) : ScanResult()
}

data class UnmatchedRom(
    val filePath: String,
    val fileName: String,
    val detectedPlatformId: String?,
)

data class FolderSetupResult(val created: Int, val existing: Int, val total: Int)

data class PcExportFile(
    val title: String,
    val extension: String,
    val idContent: String?,
    val rawPath: String?,
    val uri: String,
)

private val PC_EXPORT_EXTENSIONS = setOf("steam", "epic", "gog", "amazon", "pcgame", "desktop", "pfpgame")

private const val PFP_EXPORT_EXTENSION = "pfpgame"
private const val MAX_PFP_EXPORT_BYTES = 256L * 1024

private const val MAX_LAUNCHER_EXPORT_BYTES = 256L

private data class SafFileChild(
    val name: String,
    val rawPath: String,
    val uri: String,
)

@Singleton
class RomScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val platformExtensionMap: PlatformExtensionMap,
    private val discImageResolver: DiscImageResolver,
    private val folderHintResolver: PlatformFolderHintResolver,
    private val arcadeRomsets: ArcadeRomsetCatalog,
    private val discSetBuilder: DiscSetBuilder,
    private val discCompanionSuppressor: DiscCompanionSuppressor,
    private val m3uPlaylistReader: M3uPlaylistReader,
    private val discRegionReader: DiscRegionReader,
) {
    fun scanDirectory(
        directory: String,
        extensions: List<String>,
        platformId: String,
        recursive: Boolean,
        existingRomPaths: Set<String>,
    ): Flow<ScanResult> = flow {
        Timber.i("Memory Card scan — platform=$platformId dir=$directory exts=$extensions recursive=$recursive")

        val root = File(directory)
        if (!root.exists() || !root.isDirectory) {
            emit(ScanResult.Error("ROM directory not found: $directory"))
            return@flow
        }

        val allowed = extensions.map { it.removePrefix(".").lowercase() }.toSet()
        if (allowed.isEmpty()) {
            emit(ScanResult.Complete(emptyList(), 0, emptyList(), emptyList()))
            return@flow
        }

        val allFiles = (if (recursive) root.walkTopDown() else root.listFiles()?.asSequence() ?: emptySequence())
            .filter { it.isFile }
            .filter { !it.name.startsWith(".") }
            .toList()
        val suppressedPaths = discImageResolver.resolveFiles(allFiles).suppressedPaths

        val candidates = allFiles
            .filter { it.extension.lowercase() in allowed }
            .filterNot { it.absolutePath in suppressedPaths }

        val newGames         = mutableListOf<Game>()
        val seenPaths        = HashSet<String>()
        var alreadyInLibrary = 0
        var filesScanned     = 0

        for (file in candidates) {
            filesScanned++
            val path = file.absolutePath

            if (path in existingRomPaths || !seenPaths.add(path)) {
                alreadyInLibrary++
                continue
            }

            emit(ScanResult.Progress(
                ScanProgress(
                    currentFolder  = directory,
                    filesScanned   = filesScanned,
                    filesFound     = newGames.size,
                    totalEstimated = candidates.size,
                )
            ))

            val (title, resolvedPlatform) =
                when (val d = arcadeRomsets.decide(file.nameWithoutExtension, file.extension.lowercase(), platformId)) {
                    is ArcadeRomsetCatalog.Decision.Route -> d.title to d.platformId
                    ArcadeRomsetCatalog.Decision.Skip -> {
                        Timber.d("Skipped non-romset in CPS card: ${file.name}")
                        continue
                    }
                    ArcadeRomsetCatalog.Decision.UseDefault -> cleanRomTitle(file.nameWithoutExtension) to platformId
                }
            newGames.add(
                Game(
                    title      = title,
                    platformId = resolvedPlatform,
                    romPath    = path,
                )
            )
        }

        Timber.i("Memory Card scan complete — platform=$platformId new=${newGames.size} existing=$alreadyInLibrary")
        emit(ScanResult.Complete(
            discSetBuilder.assign(newGames, discRegionReader::read, m3uPlaylistReader::read),
            alreadyInLibrary, emptyList(), emptyList(),
            presentRomPaths = candidates.mapTo(HashSet()) { it.absolutePath },
        ))
    }.flowOn(Dispatchers.IO)

    fun scanTree(
        treeUri: String,
        extensions: List<String>,
        platformId: String,
        recursive: Boolean,
        existingRomPaths: Set<String>,

        startDocId: String? = null,
    ): Flow<ScanResult> = flow {
        Timber.i("Memory Card SAF scan — platform=$platformId tree=$treeUri exts=$extensions recursive=$recursive start=${startDocId ?: "(root)"}")

        val tree = runCatching { Uri.parse(treeUri) }.getOrNull()
        if (tree == null) {
            emit(ScanResult.Error("This library's folder link is invalid. Re-add the folder."))
            return@flow
        }
        val allowed = extensions.map { it.removePrefix(".").lowercase() }.toSet()
        if (allowed.isEmpty()) {
            emit(ScanResult.Complete(emptyList(), 0, emptyList(), emptyList()))
            return@flow
        }

        val newGames         = mutableListOf<Game>()
        val seenPaths        = HashSet<String>()
        val presentPaths     = HashSet<String>()
        var alreadyInLibrary = 0
        var filesScanned     = 0

        val visited = HashSet<String>()
        val rootDocId = startDocId ?: DocumentsContract.getTreeDocumentId(tree)
        visited.add(rootDocId)
        val stack = ArrayDeque<String>().apply { addLast(rootDocId) }
        val fileChildren = mutableListOf<SafFileChild>()
        while (stack.isNotEmpty()) {
            coroutineContext.ensureActive()
            val dirDocId = stack.removeLast()
            for (child in context.contentResolver.querySafChildren(tree, dirDocId)) {
                coroutineContext.ensureActive()
                if (child.isDirectory) {
                    if (recursive && visited.add(child.documentId)) stack.addLast(child.documentId)
                    continue
                }
                if (child.name.startsWith(".")) continue

                val rawPath = safDocumentIdToRawPath(child.documentId) ?: child.uri.toString()
                fileChildren.add(SafFileChild(child.name, rawPath, child.uri.toString()))
            }
        }

        val uriByPath = fileChildren.associate { it.rawPath to it.uri }
        val suppressedPaths = discCompanionSuppressor.suppressedFiles(
            fileChildren.map { ScannedDiscFile(it.rawPath, it.name) },
        ) { file ->
            val uri = uriByPath[file.rawPath] ?: return@suppressedFiles null
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.bufferedReader()?.readLines()
            }.getOrNull()
        }

        for (child in fileChildren) {
            coroutineContext.ensureActive()
            if (child.rawPath in suppressedPaths) continue
            val ext = child.name.substringAfterLast('.', "").lowercase()
            if (ext !in allowed) continue

            filesScanned++
            presentPaths.add(child.rawPath)
            if (child.rawPath in existingRomPaths || !seenPaths.add(child.rawPath)) {
                alreadyInLibrary++
                continue
            }

            emit(ScanResult.Progress(
                ScanProgress(
                    currentFolder  = child.name,
                    filesScanned   = filesScanned,
                    filesFound     = newGames.size,
                    totalEstimated = filesScanned,
                )
            ))

            val stem = child.name.substringBeforeLast('.', child.name)
            val (title, resolvedPlatform) =
                when (val d = arcadeRomsets.decide(stem, ext, platformId)) {
                    is ArcadeRomsetCatalog.Decision.Route -> d.title to d.platformId
                    ArcadeRomsetCatalog.Decision.Skip -> {
                        Timber.d("Skipped non-romset in CPS card: ${child.name}")
                        continue
                    }
                    ArcadeRomsetCatalog.Decision.UseDefault -> cleanRomTitle(stem) to platformId
                }
            newGames.add(
                Game(
                    title      = title,
                    platformId = resolvedPlatform,
                    romPath    = child.rawPath,
                    romUri     = child.uri,
                )
            )
        }

        Timber.i("Memory Card SAF scan complete — platform=$platformId new=${newGames.size} existing=$alreadyInLibrary")
        emit(
            ScanResult.Complete(
                discSetBuilder.assign(newGames, discRegionReader::read, m3uPlaylistReader::read),
                alreadyInLibrary, emptyList(), emptyList(),
                presentRomPaths = presentPaths,
            )
        )
    }.flowOn(Dispatchers.IO)

    suspend fun listSubfolderNames(treeUri: String): List<String> = withContext(Dispatchers.IO) {
        val tree = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return@withContext emptyList()
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
            ?: return@withContext emptyList()
        context.contentResolver.querySafChildren(tree, rootDocId)
            .filter { it.isDirectory }
            .map { it.name }
    }

    suspend fun createSubfolders(treeUri: String, names: List<String>): FolderSetupResult =
        withContext(Dispatchers.IO) {
            val tree = runCatching { Uri.parse(treeUri) }.getOrNull()
                ?: return@withContext FolderSetupResult(0, 0, names.size)
            val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
                ?: return@withContext FolderSetupResult(0, 0, names.size)
            val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootDocId)

            val existingDirs = context.contentResolver.querySafChildren(tree, rootDocId)
                .filter { it.isDirectory }
                .map { it.name.lowercase() }
                .toSet()

            var created = 0
            var existing = 0
            for (name in names) {
                if (name.lowercase() in existingDirs) { existing++; continue }
                val ok = runCatching {
                    DocumentsContract.createDocument(
                        context.contentResolver,
                        parentDocUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        name,
                    )
                }.getOrNull() != null
                if (ok) created++ else Timber.w("Could not create ROM subfolder '$name'")
            }
            Timber.i("ES-DE folder setup — created=$created existing=$existing total=${names.size}")
            FolderSetupResult(created, existing, names.size)
        }

    suspend fun scanPcFolder(treeUri: String, startDocId: String? = null): List<PcExportFile> =
        withContext(Dispatchers.IO) {
            val tree = runCatching { Uri.parse(treeUri) }.getOrNull() ?: return@withContext emptyList()
            val rootDocId = startDocId ?: runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull()
                ?: return@withContext emptyList()

            val out = mutableListOf<PcExportFile>()
            val visited = HashSet<String>().apply { add(rootDocId) }
            val stack = ArrayDeque<String>().apply { addLast(rootDocId) }
            while (stack.isNotEmpty()) {
                coroutineContext.ensureActive()
                val dirDocId = stack.removeLast()
                for (child in context.contentResolver.querySafChildren(tree, dirDocId)) {
                    coroutineContext.ensureActive()
                    if (child.isDirectory) {
                        if (visited.add(child.documentId)) stack.addLast(child.documentId)
                        continue
                    }
                    val ext = child.name.substringAfterLast('.', "").lowercase()
                    if (ext !in PC_EXPORT_EXTENSIONS) continue
                    val title = child.name.substringBeforeLast('.', child.name)

                    val readCapBytes = when (ext) {
                        PFP_EXPORT_EXTENSION -> MAX_PFP_EXPORT_BYTES
                        "desktop" -> null
                        else -> MAX_LAUNCHER_EXPORT_BYTES
                    }
                    val tooLargeHint = readCapBytes != null && (child.sizeBytes ?: 0L) > readCapBytes
                    val idContent = if (readCapBytes == null || tooLargeHint) null else {
                        val text = readBoundedText(child.uri, readCapBytes)
                        if (text == null) {
                            Timber.w("Oversized or unreadable export file, skipping content: ${child.name}")
                        }
                        text?.trim()
                    }
                    out.add(
                        PcExportFile(
                            title      = title,
                            extension  = ext,
                            idContent  = idContent,
                            rawPath    = safDocumentIdToRawPath(child.documentId),
                            uri        = child.uri.toString(),
                        )
                    )
                }
            }
            Timber.i("PC folder scan — found ${out.size} export file(s)")
            out
        }

    private fun readBoundedText(uri: Uri, maxBytes: Long): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val cap = maxBytes.toInt()
            val chunk = ByteArray(minOf(cap, 8192).coerceAtLeast(1))
            val out = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = stream.read(chunk)
                if (read == -1) break
                total += read
                if (total > cap) return@use null
                out.write(chunk, 0, read)
            }
            out.toString(Charsets.UTF_8.name())
        }
    }.getOrNull()

    suspend fun findMissingRoms(knownPaths: List<String>): List<String> =
        knownPaths.filter { path -> !File(path).exists() }

    private fun String.sanitizeRomName(): String = cleanRomTitle(this)
}

fun safDocumentIdToRawPath(documentId: String): String? {
    val parts = documentId.split(":", limit = 2)
    if (parts.size != 2 || parts[1].isBlank()) return null
    val (volume, relative) = parts
    return if (volume.equals("primary", ignoreCase = true)) {
        "/storage/emulated/0/$relative"
    } else {
        "/storage/$volume/$relative"
    }
}

fun cleanRomTitle(raw: String): String {
    var title = raw
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("\\[[^]]*]"), " ")
        .replace('_', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()

    title = title.replace(Regex("\\s-\\s"), ": ")

    return title.trim().trim(':', '-', ' ').ifBlank { raw.trim() }
}
