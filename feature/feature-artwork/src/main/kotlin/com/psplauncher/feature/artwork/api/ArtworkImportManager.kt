package com.psplauncher.feature.artwork.api

import android.content.Context
import android.net.Uri
import com.psplauncher.core.data.database.dao.ArtworkImportReportDao
import com.psplauncher.core.data.database.dao.ArtworkRecordDao
import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.entity.ArtworkImportReportEntity
import com.psplauncher.core.data.database.entity.ArtworkRecordEntity
import com.psplauncher.core.data.repository.ArtworkFolderRepository
import com.psplauncher.core.data.repository.ArtworkStorageMode
import com.psplauncher.core.data.saf.SafChild
import com.psplauncher.feature.artwork.importer.ArtworkImportMatcher
import com.psplauncher.feature.artwork.importer.ArtworkImportPlanner
import com.psplauncher.feature.artwork.importer.ArtworkImportWorker
import com.psplauncher.feature.artwork.importer.DetectedImportSource
import com.psplauncher.feature.artwork.importer.ImportPlan
import com.psplauncher.feature.artwork.importer.ImportSummary
import com.psplauncher.feature.artwork.importer.RelinkOwnerLookup
import com.psplauncher.feature.artwork.importer.RelinkSlotOrdering
import com.psplauncher.feature.artwork.portable.ArtworkIdentityIndex
import com.psplauncher.feature.artwork.portable.ArtworkIdentityRecorder
import com.psplauncher.feature.artwork.portable.ArtworkLibraryManifest
import com.psplauncher.feature.artwork.portable.ArtworkNaming
import com.psplauncher.feature.artwork.portable.ArtworkPathResolver
import com.psplauncher.feature.artwork.portable.PortableArtworkLibrary
import com.psplauncher.feature.artwork.store.ArtworkFileNaming
import com.psplauncher.feature.artwork.store.ArtworkKind
import com.psplauncher.feature.artwork.store.ArtworkStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val GRID_ASPECT_THRESHOLD = 1.6f

@Singleton
class ArtworkImportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val folderRepository: ArtworkFolderRepository,
    private val library: PortableArtworkLibrary,
    private val planner: ArtworkImportPlanner,
    private val reportDao: ArtworkImportReportDao,
    private val gameDao: GameDao,
    private val artworkRecordDao: ArtworkRecordDao,
    private val artworkStore: ArtworkStore,
    private val internalStore: com.psplauncher.feature.artwork.store.InternalArtworkStore,
    private val identityRecorder: ArtworkIdentityRecorder,
) {
    data class LinkResult(
        val manifest: ArtworkLibraryManifest,

        val existingLibrary: Boolean,
    )

    data class ReportRow(val entity: ArtworkImportReportEntity, val summary: ImportSummary?)

    val folderTreeUri: Flow<String?> get() = folderRepository.treeUri

    val reports: Flow<List<ReportRow>> = reportDao.observeAll().map { rows ->
        rows.map { ReportRow(it, ImportSummary.parse(it.summaryJson)) }
    }

    suspend fun hasLiveGrant(): Boolean = folderRepository.hasLiveGrant()

    suspend fun linkFolder(treeUri: Uri): LinkResult? {
        folderRepository.persist(treeUri)
        val existing = library.readManifest(treeUri)
        val manifest = existing ?: library.ensureLibrary(treeUri, appVersion()) ?: run {
            Timber.w("Could not initialize artwork library at $treeUri")
            return null
        }
        folderRepository.setTreeUri(treeUri.toString())
        folderRepository.setStorageMode(ArtworkStorageMode.PORTABLE)
        folderRepository.setLibraryUuid(manifest.libraryUuid)
        library.clearDirCache()
        return LinkResult(manifest, existingLibrary = existing != null)
    }

    suspend fun forgetFolder() = folderRepository.forget()

    suspend fun detectSources(): List<DetectedImportSource> {
        val tree = linkedTree() ?: return emptyList()
        return planner.detectSources(tree)
    }

    suspend fun unrecognizedFolders(detected: List<DetectedImportSource>): List<String> {
        val tree = linkedTree() ?: return emptyList()
        return planner.unrecognizedFolders(tree, detected)
    }

    suspend fun buildPlan(detected: DetectedImportSource): ImportPlan? {
        val tree = linkedTree() ?: return null
        return planner.plan(tree, detected)
    }

    fun startImport(plan: ImportPlan, transfer: PortableArtworkLibrary.Transfer): UUID =
        ArtworkImportWorker.enqueue(context, plan, transfer)

    fun cancelImport() {
        ArtworkImportWorker.cancel(context)
    }

    suspend fun assignAmbiguous(plan: ImportPlan, index: Int, gameId: Long): ImportPlan =
        planner.assignAmbiguous(plan, index, gameId)

    fun skipAmbiguous(plan: ImportPlan, index: Int): ImportPlan =
        planner.skipAmbiguous(plan, index)

    suspend fun clearReports() = reportDao.clear()

    fun startExport(destTreeUri: Uri): UUID {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                destTreeUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { Timber.w(it, "Could not persist export destination grant") }
        return com.psplauncher.feature.artwork.export.ArtworkExportWorker.enqueue(context, destTreeUri)
    }

    suspend fun internalArtworkFootprint(): Pair<Int, Long> = internalStore.footprint()

    fun startInternalMigration(): UUID =
        com.psplauncher.feature.artwork.migrate.InternalArtworkMigrationWorker.enqueue(context)

    fun cancelInternalMigration() =
        com.psplauncher.feature.artwork.migrate.InternalArtworkMigrationWorker.cancel(context)

    data class RelinkResult(
        val entriesScanned: Int,
        val gamesLinked: Int,
        val orphanEntries: Int,
        val missingFiles: Int = 0,
        val changedFiles: Int = 0,
        val duplicateNames: Int = 0,
    )

    private data class MultiAssetFile(
        val fileStem: String,
        val ordinal: Int,
        val documentUri: String,
        val sizeBytes: Long,
        val relativePath: String,
    )

    suspend fun migrateV1IfNeeded(): Int = withContext(Dispatchers.IO) {
        val tree = linkedTree() ?: return@withContext 0
        val v1Assets = library.migrateV1Library(tree).assets.size
        val v2Dirs = library.migrateRootPlatformsToArtwork(tree)
        val icon0Moves = relocateIcon0Assets(tree)
        val manifest = library.readManifest(tree)
        if (manifest != null && manifest.formatVersion < ArtworkLibraryManifest.FORMAT_VERSION) {
            library.writeManifest(tree, manifest.copy(formatVersion = ArtworkLibraryManifest.FORMAT_VERSION))
        }
        val relocated = v1Assets + v2Dirs + icon0Moves
        if (relocated > 0) {
            library.clearDirCache()
            relinkLibrary()
            Timber.i(
                "Library layout upgraded: $v1Assets v1 assets + $v2Dirs platform dirs + " +
                    "$icon0Moves icons relocated",
            )
        }
        relocated
    }

    private suspend fun relocateIcon0Assets(tree: Uri): Int {
        var moved = 0

        val stale = artworkRecordDao.getAll().filter {
            it.artworkType == ArtworkKind.ICON.name &&
                !it.relativePath.contains("/${ArtworkPathResolver.DIR_ICON0}/")
        }
        for (record in stale) {
            val fileName = record.relativePath.substringAfterLast('/')
            if (fileName.isBlank()) continue
            val saved = library.relocateAsset(
                tree, record.platformId,
                fromKind = ArtworkKind.BOX_ART,
                toKind = ArtworkKind.ICON,
                fileName = fileName,
            ) ?: continue
            val oldUri = record.documentUri
            artworkRecordDao.upsert(
                record.copy(
                    relativePath = ArtworkPathResolver.relativePath(record.platformId, ArtworkKind.ICON, saved.fileName),
                    documentUri = saved.uriString,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            gameDao.getById(record.gameId)?.let { game ->
                if (game.iconUri == oldUri || !artworkStore.isValidRef(game.iconUri)) {
                    gameDao.updateIconUri(record.gameId, saved.uriString)
                }
            }
            moved++
        }

        val gridRecords = artworkRecordDao.getAll().filter {
            it.artworkType == ArtworkKind.BOX_ART.name && isGridShaped(it.documentUri)
        }
        for (record in gridRecords) {
            val game = gameDao.getById(record.gameId) ?: continue
            if (game.boxArtUri == record.documentUri) gameDao.updateBoxArt(record.gameId, null)
            val fileName = record.relativePath.substringAfterLast('/')
            val hasIconRecord = artworkRecordDao.get(record.gameId, ArtworkKind.ICON.name) != null
            if (!hasIconRecord && fileName.isNotBlank()) {
                val saved = library.relocateAsset(
                    tree, record.platformId,
                    fromKind = ArtworkKind.BOX_ART,
                    toKind = ArtworkKind.ICON,
                    fileName = fileName,
                )
                if (saved != null) {
                    artworkRecordDao.deleteById(record.id)
                    artworkRecordDao.upsert(
                        record.copy(
                            id = 0,
                            artworkType = ArtworkKind.ICON.name,
                            relativePath = ArtworkPathResolver.relativePath(record.platformId, ArtworkKind.ICON, saved.fileName),
                            documentUri = saved.uriString,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                    gameDao.updateIconUri(record.gameId, saved.uriString)
                    moved++
                }
            } else {
                artworkRecordDao.deleteById(record.id)
            }
        }
        return moved
    }

    private fun isGridShaped(uriString: String): Boolean = runCatching {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
            android.graphics.BitmapFactory.decodeStream(it, null, opts)
        }
        opts.outWidth > 0 && opts.outHeight > 0 &&
            opts.outWidth.toFloat() / opts.outHeight >= GRID_ASPECT_THRESHOLD
    }.getOrDefault(false)

    suspend fun relinkLibrary(
        claims: Map<Triple<String, String, String>, Long> = emptyMap(),
        identitySeeds: List<ArtworkIdentityIndex.Entry> = emptyList(),
    ): RelinkResult? = withContext(Dispatchers.IO) {
        val tree = linkedTree() ?: return@withContext null
        if (!folderRepository.hasLiveGrant()) return@withContext null

        relocateIcon0Assets(tree)
        val rootDocId = android.provider.DocumentsContract.getTreeDocumentId(tree)

        val games = gameDao.getAll()
        val byPlatform = games.groupBy { it.platformId }
        val indexes = HashMap<String, ArtworkImportMatcher.PlatformIndex>()
        fun indexFor(platformId: String) = indexes.getOrPut(platformId) {
            ArtworkImportMatcher.PlatformIndex(
                byPlatform[platformId].orEmpty().map { g ->
                    ArtworkImportMatcher.GameRef(
                        id = g.id,
                        romStem = g.romPath?.replace('\\', '/')?.substringAfterLast('/')
                            ?.let { ArtworkNaming.fileStem(it) },
                        displayTitle = g.userTitleOverride ?: g.scrapedTitle ?: g.title,
                        scrapedTitle = g.scrapedTitle,
                    )
                },
            )
        }

        val priorRecords = artworkRecordDao.getAll()
            .associateBy { Triple(it.gameId, it.artworkType, it.sortOrder) }

        val priorByName = priorRecords.values.associateBy {
            Triple(it.gameId, it.artworkType, it.portableName.lowercase())
        }
        fun lockedTypes(gameId: Long): Set<String> = priorRecords.values
            .filter { it.gameId == gameId && (it.locked || it.userAssigned) }
            .map { it.artworkType }.toSet()

        val ownersByName = HashMap<Triple<String, String, String>, MutableSet<Long>>()
        priorRecords.values.forEach { r ->
            ownersByName.getOrPut(Triple(r.platformId, r.artworkType, r.portableName.lowercase())) { mutableSetOf() }
                .add(r.gameId)
        }

        if (identitySeeds.isNotEmpty()) identityRecorder.recordAll(tree, identitySeeds)
        val identityIndex = identityRecorder.current(tree)
        val ownersByToken = HashMap<String, MutableSet<Long>>()
        games.forEach { g ->
            ArtworkIdentityIndex.tokensOf(
                romCrc32 = g.romCrc32, ssId = g.ssId,
                igdbId = g.igdbId, sgdbId = g.steamGridDbId, artworkKey = g.artworkKey,
            ).forEach { token -> ownersByToken.getOrPut(token) { mutableSetOf() }.add(g.id) }
        }

        val matchedPriorIds = HashSet<Long>()

        val identityRows = mutableListOf<ArtworkIdentityIndex.Entry>()

        var scanned = 0
        var linkedGames = 0
        var orphans = 0
        var changedFiles = 0
        var duplicateNames = 0
        val linkedIds = mutableSetOf<Long>()

        for (platformDir in library.platformDirs(tree)) {
            val platformId = platformDir.name
            if (byPlatform[platformId].isNullOrEmpty()) continue

            val mediaDirs = mutableListOf<Pair<ArtworkKind, SafChild>>()
            for (child in library.listChildren(tree, platformDir.documentId).filter { it.isDirectory }) {
                if (child.name.equals(ArtworkPathResolver.DIR_PFP, ignoreCase = true)) {
                    library.listChildren(tree, child.documentId)
                        .filter { it.isDirectory }
                        .forEach { sub ->
                            ArtworkPathResolver.kindForMediaDir("${ArtworkPathResolver.DIR_PFP}/${sub.name}")
                                ?.let { mediaDirs += it to sub }
                        }
                } else {
                    ArtworkPathResolver.kindForMediaDir(child.name)?.let { mediaDirs += it to child }
                }
            }
            for ((kind, mediaDir) in mediaDirs) {
                val records = mutableListOf<ArtworkRecordEntity>()
                val stemsInDir = HashSet<String>()

                val multiAssetByGame = LinkedHashMap<Long, MutableList<MultiAssetFile>>()
                val supersededPriorIds = mutableListOf<Long>()
                for (file in library.listChildren(tree, mediaDir.documentId)) {
                    if (file.isDirectory || (file.sizeBytes ?: 0L) <= 0L) continue
                    scanned++

                    if (kind == ArtworkKind.BOX_ART && isGridShaped(file.uri.toString())) {
                        orphans++
                        continue
                    }
                    val fileStem = ArtworkNaming.fileStem(file.name)
                    val stemLower = fileStem.lowercase()
                    if (!stemsInDir.add(stemLower)) duplicateNames++

                    val multi = ArtworkFileNaming.supportsMultiple(kind)
                    val baseStem = if (multi) ArtworkFileNaming.stripOrdinal(fileStem) else fileStem
                    val ids = RelinkOwnerLookup.owners(
                        platformId = platformId,
                        kind = kind.name,
                        fileName = file.name,
                        fileStem = fileStem,
                        baseStem = baseStem,
                        claims = claims,
                        recordOwners = ownersByName,
                        fuzzyMatch = { name ->
                            (indexFor(platformId).match(name) as? ArtworkImportMatcher.Result.Matched)?.gameIds
                        },

                        identityOwners = { stem ->
                            identityIndex.find(platformId, kind.name, stem)?.let { row ->
                                row.tokens()
                                    .firstNotNullOfOrNull { token -> ownersByToken[token] }
                                    ?.toList()
                                    ?: emptyList()
                            }
                        },
                    )
                    if (ids.isNullOrEmpty()) { orphans++; continue }
                    val uri = file.uri.toString()
                    val size = file.sizeBytes ?: 0L

                    if (multi) {
                        for (gameId in ids) {
                            val game = games.firstOrNull { it.id == gameId } ?: continue
                            identityRows += ArtworkIdentityIndex.Entry(
                                platformId = platformId,
                                kind = kind.name,
                                portableName = fileStem,
                                romCrc32 = game.romCrc32,
                                ssId = game.ssId,
                                igdbId = game.igdbId,
                                sgdbId = game.steamGridDbId,
                                artworkKey = game.artworkKey,
                            )
                            multiAssetByGame.getOrPut(gameId) { mutableListOf() } += MultiAssetFile(
                                fileStem = fileStem,
                                ordinal = ArtworkFileNaming.ordinalOf(fileStem),
                                documentUri = uri,
                                sizeBytes = size,
                                relativePath = ArtworkPathResolver.relativePath(platformId, kind, file.name),
                            )
                        }
                        continue
                    }

                    val sortOrder = 0
                    for (gameId in ids) {
                        val game = games.firstOrNull { it.id == gameId } ?: continue

                        val isColumnKind = sortOrder == 0 && (
                            kind == ArtworkKind.ICON || kind == ArtworkKind.HERO ||
                                kind == ArtworkKind.BACKGROUND || kind == ArtworkKind.LOGO ||
                                kind == ArtworkKind.BOX_ART || kind == ArtworkKind.PHYSICAL_MEDIA ||
                                kind == ArtworkKind.BOX_3D
                            )
                        if (isColumnKind && kind.name !in lockedTypes(gameId)) {
                            val current = when (kind) {
                                ArtworkKind.ICON -> game.iconUri
                                ArtworkKind.HERO -> game.heroUri
                                ArtworkKind.BACKGROUND -> game.artworkUri
                                ArtworkKind.BOX_ART -> game.boxArtUri
                                ArtworkKind.PHYSICAL_MEDIA -> game.physicalMediaUri
                                ArtworkKind.BOX_3D -> game.box3dUri
                                else -> game.logoUri
                            }

                            val replaceable = !artworkStore.isValidRef(current) ||
                                current?.startsWith("http", ignoreCase = true) == true
                            if (replaceable) {
                                when (kind) {
                                    ArtworkKind.ICON -> gameDao.updateIconUri(gameId, uri)
                                    ArtworkKind.HERO -> gameDao.updateHero(gameId, uri)
                                    ArtworkKind.BACKGROUND -> gameDao.updateArtwork(gameId, uri)
                                    ArtworkKind.BOX_ART -> gameDao.updateBoxArt(gameId, uri)
                                    ArtworkKind.PHYSICAL_MEDIA -> gameDao.updatePhysicalMedia(gameId, uri)
                                    ArtworkKind.BOX_3D -> gameDao.updateBox3d(gameId, uri)
                                    else -> gameDao.updateLogo(gameId, uri)
                                }
                                linkedIds.add(gameId)
                            }
                        }

                        if (kind == ArtworkKind.HERO && sortOrder == 0 &&
                            ArtworkKind.BACKGROUND.name !in lockedTypes(gameId)
                        ) {
                            val bg = game.artworkUri
                            if (!artworkStore.isValidRef(bg) || bg?.startsWith("http", ignoreCase = true) == true) {
                                gameDao.updateArtwork(gameId, uri)
                                linkedIds.add(gameId)
                            }
                        }

                        val prior = priorRecords[Triple(gameId, kind.name, sortOrder)]
                        if (prior != null && prior.sizeBytes != size) changedFiles++
                        prior?.let { matchedPriorIds.add(it.id) }
                        identityRows += ArtworkIdentityIndex.Entry(
                            platformId = platformId,
                            kind = kind.name,
                            portableName = fileStem,
                            romCrc32 = game.romCrc32,
                            ssId = game.ssId,
                            igdbId = game.igdbId,
                            sgdbId = game.steamGridDbId,
                            artworkKey = game.artworkKey,
                        )
                        records += ArtworkRecordEntity(
                            gameId = gameId,
                            platformId = platformId,
                            artworkType = kind.name,
                            sortOrder = sortOrder,
                            portableName = fileStem,
                            relativePath = ArtworkPathResolver.relativePath(platformId, kind, file.name),
                            documentUri = uri,
                            source = prior?.source ?: "relink",
                            sizeBytes = size,
                            userAssigned = prior?.userAssigned ?: false,
                            locked = prior?.locked ?: false,
                            originUrl = prior?.originUrl,
                            provider = prior?.provider,
                            providerAssetId = prior?.providerAssetId,
                            cropRect = prior?.cropRect,
                            hasOriginal = prior?.hasOriginal ?: false,
                            cropProfileKey = prior?.cropProfileKey,
                            createdAt = prior?.createdAt ?: System.currentTimeMillis(),
                        )
                    }
                }

                for ((gameId, files) in multiAssetByGame) {
                    val ordered = RelinkSlotOrdering.order(
                        files = files,
                        stemOf = { it.fileStem },
                        ordinalOf = { it.ordinal },
                        priorSortOrder = { nameLower -> priorByName[Triple(gameId, kind.name, nameLower)]?.sortOrder },
                    )
                    ordered.forEachIndexed { position, f ->
                        val prior = priorByName[Triple(gameId, kind.name, f.fileStem.lowercase())]
                        if (prior != null && prior.sizeBytes != f.sizeBytes) changedFiles++
                        prior?.let { matchedPriorIds.add(it.id) }
                        records += ArtworkRecordEntity(
                            gameId = gameId,
                            platformId = platformId,
                            artworkType = kind.name,
                            sortOrder = position,
                            portableName = f.fileStem,
                            relativePath = f.relativePath,
                            documentUri = f.documentUri,
                            source = prior?.source ?: "relink",
                            sizeBytes = f.sizeBytes,
                            userAssigned = prior?.userAssigned ?: false,
                            locked = prior?.locked ?: false,
                            originUrl = prior?.originUrl,
                            provider = prior?.provider,
                            providerAssetId = prior?.providerAssetId,
                            cropRect = prior?.cropRect,
                            hasOriginal = prior?.hasOriginal ?: false,
                            cropProfileKey = prior?.cropProfileKey,
                            createdAt = prior?.createdAt ?: System.currentTimeMillis(),
                        )
                    }

                    priorRecords.values
                        .filter { it.gameId == gameId && it.artworkType == kind.name && it.sortOrder >= ordered.size }
                        .filter { it.id in matchedPriorIds }
                        .mapTo(supersededPriorIds) { it.id }
                }
                if (records.isNotEmpty()) artworkRecordDao.upsert(records)
                supersededPriorIds.forEach { artworkRecordDao.deleteById(it) }
            }
        }

        var missingFiles = 0
        for (prior in priorRecords.values) {
            if (prior.id in matchedPriorIds) continue
            missingFiles++
            artworkRecordDao.deleteById(prior.id)
            val game = games.firstOrNull { it.id == prior.gameId } ?: continue

            if (prior.sortOrder != 0) continue
            when (prior.artworkType) {
                ArtworkKind.ICON.name -> if (game.iconUri == prior.documentUri) gameDao.updateIconUri(game.id, null)
                ArtworkKind.HERO.name -> if (game.heroUri == prior.documentUri) gameDao.updateHero(game.id, null)
                ArtworkKind.BACKGROUND.name -> if (game.artworkUri == prior.documentUri) gameDao.updateArtwork(game.id, null)
                ArtworkKind.LOGO.name -> if (game.logoUri == prior.documentUri) gameDao.updateLogo(game.id, null)
                ArtworkKind.BOX_ART.name -> if (game.boxArtUri == prior.documentUri) gameDao.updateBoxArt(game.id, null)
                ArtworkKind.PHYSICAL_MEDIA.name -> if (game.physicalMediaUri == prior.documentUri) gameDao.updatePhysicalMedia(game.id, null)
                ArtworkKind.BOX_3D.name -> if (game.box3dUri == prior.documentUri) gameDao.updateBox3d(game.id, null)
            }
        }

        identityRecorder.recordAll(tree, identityRows)
        identityRecorder.flush(tree)

        linkedGames = linkedIds.size
        Timber.i(
            "Scan: $scanned files, $linkedGames linked, $orphans unmatched, " +
                "$missingFiles missing, $changedFiles changed, $duplicateNames duplicate names",
        )
        RelinkResult(scanned, linkedGames, orphans, missingFiles, changedFiles, duplicateNames)
    }

    private suspend fun linkedTree(): Uri? =
        folderRepository.getTreeUri()?.let { Uri.parse(it) }

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: ""
}
