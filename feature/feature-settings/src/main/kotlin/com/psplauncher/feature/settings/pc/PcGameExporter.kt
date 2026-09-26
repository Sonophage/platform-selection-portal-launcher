package com.psplauncher.feature.settings.pc

import com.psplauncher.core.domain.model.PlatformIds.WINDOWS as WINDOWS_PLATFORM_ID

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.psplauncher.core.data.database.dao.ArtworkRecordDao
import com.psplauncher.core.data.database.entity.ArtworkRecordEntity
import com.psplauncher.core.data.model.IntentUriExtras
import com.psplauncher.core.data.repository.WindowsLibrarySetup
import com.psplauncher.core.data.repository.WindowsSetupState
import com.psplauncher.core.data.saf.SafChild
import com.psplauncher.core.data.saf.querySafChildren
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.feature.artwork.portable.PortableNameResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class PcGameExportFile(
    val fileName: String,
    val export: PcGameExport,
)

data class PcGameExportReport(
    val written: Int,
    val skipped: Int,
    val failed: Int,
    val message: String,
)

object PcGameExportBuilder {
    fun build(
        games: List<Game>,
        artworkByGame: Map<Long, List<ArtworkRecordEntity>>,
        existing: Map<String, PcGameExport?> = emptyMap(),
    ): List<PcGameExportFile> {
        val taken = HashMap(existing)
        return games.mapNotNull { game ->
            val export = exportFor(game, artworkByGame[game.id].orEmpty()) ?: return@mapNotNull null
            val fileName = fileNameFor(game, taken)
            taken[fileName.lowercase()] = null
            PcGameExportFile(fileName, export)
        }
    }

    fun exportFor(game: Game, artwork: List<ArtworkRecordEntity>): PcGameExport? {
        val launcherPackage = game.packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val isPin = game.shortcutId != null
        if (!isPin && game.launchIntentUri == null) return null
        return PcGameExport(
            title = game.title,
            scrapedTitle = game.scrapedTitle,
            userTitleOverride = game.userTitleOverride,
            launcherPackage = launcherPackage,

            launchIntentUri = if (isPin) null else game.launchIntentUri,
            shortcutId = game.shortcutId,
            storefront = game.storefront,
            storefrontGameId = game.storefrontGameId,
            ssId = game.ssId,
            igdbId = game.igdbId,
            steamGridDbId = game.steamGridDbId,
            artwork = artwork
                .sortedWith(compareBy({ it.artworkType }, { it.sortOrder }))

                .take(PcGameExportCodec.MAX_ARTWORK_ITEMS)
                .map { PcGameExportArtwork(kind = it.artworkType, sortOrder = it.sortOrder, portableName = it.portableName) },
        )
    }

    fun isSameGame(existing: PcGameExport, game: Game): Boolean {
        if (existing.launcherPackage != game.packageName) return false
        val shortcutId = game.shortcutId
        if (shortcutId != null) return existing.shortcutId == shortcutId
        if (existing.shortcutId != null) return false
        val mine = game.launchIntentUri ?: return false
        val theirs = existing.launchIntentUri ?: return false
        if (mine == theirs) return true
        val myExtras = IntentUriExtras.parse(mine)
        return myExtras.isNotEmpty() && myExtras == IntentUriExtras.parse(theirs)
    }

    fun fileNameFor(game: Game, existing: Map<String, PcGameExport?>): String {
        val base = PortableNameResolver.fromTitle(game.displayTitle)
        var name = base
        var suffix = 2
        while (true) {
            val fileName = "$name.${PcGameExportCodec.EXTENSION}"
            val key = fileName.lowercase()
            if (key !in existing) return fileName
            val file = existing[key]
            if (file != null && isSameGame(file, game)) return fileName
            name = "$base (${suffix++})"
        }
    }
}

@Singleton
class PcGameExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val windowsLibrarySetup: WindowsLibrarySetup,
    private val gameRepository: GameRepository,
    private val artworkRecordDao: ArtworkRecordDao,
    private val pcGameScanner: PcGameScanner,
) {
    suspend fun export(): PcGameExportReport = withContext(Dispatchers.IO) {
        val folder = when (val found = importFolder()) {
            is ImportFolder.Missing -> return@withContext refusal(found.message)
            is ImportFolder.Found -> found
        }

        val games = gameRepository.getByPlatform(WINDOWS_PLATFORM_ID)
        val artwork = games.associate { it.id to artworkRecordDao.getForGame(it.id) }
        val launcherExports = pcGameScanner.launcherExports()
        val selection = ManualGameExportSelector.select(
            windowsGames = games,
            gamesWithArtwork = artwork.filterValues { it.isNotEmpty() }.keys,
            launcherFiles = launcherExports.files,
            reproducedIntentUris = launcherExports.intentUris,
        )
        val folderFiles = context.contentResolver.querySafChildren(folder.tree, folder.docId)
            .filterNot { it.isDirectory }
        val existingByName = folderFiles.associateBy { it.name.lowercase() }
        val existingExports = folderFiles
            .filter { it.name.endsWith(".${PcGameExportCodec.EXTENSION}", ignoreCase = true) }
            .associate { it.name.lowercase() to readExport(it) }

        val files = PcGameExportBuilder.build(selection.exported, artwork, existingExports)

        val written = files.count { file ->
            write(folder.tree, folder.docId, existingByName[file.fileName.lowercase()]?.uri, file)
        }

        val failed = files.size - written
        val skipped = selection.skipped + (selection.exported.size - files.size)
        Timber.i("Manual PC game export — written=$written skipped=$skipped failed=$failed")
        PcGameExportReport(written, skipped, failed, bulkMessage(written, skipped, failed))
    }

    suspend fun exportGame(gameId: Long): PcGameExportReport = withContext(Dispatchers.IO) {
        val game = gameRepository.getById(gameId)
            ?: return@withContext refusal("That game is no longer in the library.")
        if (game.platformId != WINDOWS_PLATFORM_ID) return@withContext refusal("Only PC games can be exported.")
        val export = PcGameExportBuilder.exportFor(game, artworkRecordDao.getForGame(game.id))
            ?: return@withContext refusal("${game.displayTitle} has no launcher to bring it back with, so it can't be exported.")
        val folder = when (val found = importFolder()) {
            is ImportFolder.Missing -> return@withContext refusal(found.message)
            is ImportFolder.Found -> found
        }

        val pfpFiles = context.contentResolver.querySafChildren(folder.tree, folder.docId)
            .filter { !it.isDirectory && it.name.endsWith(".${PcGameExportCodec.EXTENSION}", ignoreCase = true) }
        val fileName = PcGameExportBuilder.fileNameFor(game, pfpFiles.associate { it.name.lowercase() to readExport(it) })
        val target = pfpFiles.firstOrNull { it.name.equals(fileName, ignoreCase = true) }?.uri

        val written = write(folder.tree, folder.docId, target, PcGameExportFile(fileName, export))
        Timber.i("PC game export — gameId=$gameId file=$fileName written=$written")
        if (written) {
            PcGameExportReport(1, 0, 0, "Exported ${game.displayTitle} to windows/import as $fileName.")
        } else {
            PcGameExportReport(0, 0, 1, "Couldn't write $fileName to windows/import.")
        }
    }

    private sealed interface ImportFolder {
        data class Found(val tree: Uri, val docId: String) : ImportFolder
        data class Missing(val message: String) : ImportFolder
    }

    private suspend fun importFolder(): ImportFolder {
        val setup = runCatching { windowsLibrarySetup.ensure() }.getOrNull()
        if (setup is WindowsSetupState.NoRomRoot) {
            return ImportFolder.Missing("Add a ROM Root first — PSP exports PC games into <root>/windows/import.")
        }
        val (treeUri, docId) = windowsLibrarySetup.importFolders().firstOrNull()
            ?: return ImportFolder.Missing("Couldn't open <windows>/import. Relink the ROM Root and try again.")
        return ImportFolder.Found(Uri.parse(treeUri), docId)
    }

    private fun readExport(file: SafChild): PcGameExport? {
        if ((file.sizeBytes ?: 0L) > PcGameExportCodec.MAX_CHARS) return null
        val text = readBoundedText(file.uri, PcGameExportCodec.MAX_CHARS.toLong()) ?: return null
        return (PcGameExportCodec.decode(text) as? PcGameExportDecode.Valid)?.export
    }

    private fun readBoundedText(uri: Uri, maxBytes: Long): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val cap = maxBytes.toInt()
            val chunk = ByteArray(minOf(cap, 8192).coerceAtLeast(1))
            val out = java.io.ByteArrayOutputStream()
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

    private fun write(tree: Uri, parentDocId: String, existing: Uri?, file: PcGameExportFile): Boolean {
        return runCatching {
            val target = existing ?: DocumentsContract.createDocument(
                context.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(tree, parentDocId),
                MIME_BINARY,
                file.fileName,
            ) ?: return false
            val bytes = PcGameExportCodec.encode(file.export).toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(target, "wt")?.use { it.write(bytes) } != null
        }.getOrElse { e ->
            Timber.w(e, "Could not write ${file.fileName}")
            false
        }
    }

    private fun refusal(message: String) = PcGameExportReport(0, 0, 0, message)

    private fun bulkMessage(written: Int, skipped: Int, failed: Int): String = when {
        written == 0 && failed == 0 ->
            "No PC games need an export file: launcher exports and pins without artwork come back on their own."
        else -> buildString {
            append("Exported $written PC game(s) to windows/import")
            if (skipped > 0) append(" · $skipped need no file")
            if (failed > 0) append(" · $failed couldn't be written")
            append(".")
        }
    }

    private companion object {
        const val MIME_BINARY = "application/octet-stream"
    }
}
