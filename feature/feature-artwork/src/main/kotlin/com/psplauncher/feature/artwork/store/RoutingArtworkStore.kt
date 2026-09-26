package com.psplauncher.feature.artwork.store

import android.content.Context
import android.net.Uri
import com.psplauncher.core.data.database.dao.ArtworkRecordDao
import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.entity.ArtworkRecordEntity
import com.psplauncher.core.data.database.entity.GameEntity
import com.psplauncher.core.data.repository.ArtworkFolderRepository
import com.psplauncher.feature.artwork.api.ArtworkImageCache
import com.psplauncher.feature.artwork.portable.ArtworkIdentityIndex
import com.psplauncher.feature.artwork.portable.ArtworkIdentityRecorder
import com.psplauncher.feature.artwork.portable.ArtworkPathResolver
import com.psplauncher.feature.artwork.portable.PortableArtworkLibrary
import com.psplauncher.feature.artwork.portable.PortableNameResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutingArtworkStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val internal: InternalArtworkStore,
    private val library: PortableArtworkLibrary,
    private val folderRepository: ArtworkFolderRepository,
    private val gameDao: GameDao,
    private val artworkRecordDao: ArtworkRecordDao,
    private val httpClient: HttpClient,
    private val imageCache: ArtworkImageCache,
    private val identityRecorder: ArtworkIdentityRecorder,
) : ArtworkStore {
    override suspend fun saveFromUrl(gameId: Long, kind: ArtworkKind, url: String, sortOrder: Int): String? {
        val target = portableTarget(gameId) ?: return internal.saveFromUrl(gameId, kind, url, sortOrder)
        val (tree, game) = target

        artworkRecordDao.getAt(gameId, kind.name, sortOrder)?.let { record ->
            if (record.locked || record.userAssigned || internal.isValidRef(record.documentUri)) {
                return record.documentUri
            }
        }
        val tmp = ArtworkTempIO.downloadToTemp(httpClient, context.cacheDir, kind, url) ?: return null
        return persistPortable(
            tree, game, kind, tmp, source = SOURCE_SCRAPE, userAssigned = false, sortOrder = sortOrder,
        )
    }

    override suspend fun saveVersionedFromUrl(gameId: Long, kind: ArtworkKind, url: String): String? {
        val target = portableTarget(gameId) ?: return internal.saveVersionedFromUrl(gameId, kind, url)
        val (tree, game) = target
        val tmp = ArtworkTempIO.downloadToTemp(httpClient, context.cacheDir, kind, url) ?: return null
        return persistPortable(tree, game, kind, tmp, source = SOURCE_USER, userAssigned = true)
    }

    override suspend fun saveVersionedFromUri(gameId: Long, kind: ArtworkKind, uri: Uri): String? {
        val target = portableTarget(gameId) ?: return internal.saveVersionedFromUri(gameId, kind, uri)
        val (tree, game) = target
        val tmp = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use {
                    ArtworkTempIO.copyToTemp(it, context.cacheDir, kind)
                }
            }.onFailure { Timber.e(it, "Failed to read picked artwork $uri") }.getOrNull()
        } ?: return null
        return persistPortable(tree, game, kind, tmp, source = SOURCE_USER, userAssigned = true)
    }

    override suspend fun saveFromFile(
        gameId: Long,
        kind: ArtworkKind,
        tempFile: java.io.File,
        sortOrder: Int,
    ): String? {
        val target = portableTarget(gameId) ?: return internal.saveFromFile(gameId, kind, tempFile, sortOrder)
        val (tree, game) = target
        return persistPortable(
            tree, game, kind, tempFile, source = SOURCE_SCRAPE, userAssigned = false, sortOrder = sortOrder,
        )
    }

    override fun isValidRef(ref: String?): Boolean = internal.isValidRef(ref)

    override suspend fun find(gameId: Long, kind: ArtworkKind, sortOrder: Int): String? =
        internal.find(gameId, kind, sortOrder)
            ?: artworkRecordDao.getAt(gameId, kind.name, sortOrder)?.documentUri?.takeIf { internal.isValidRef(it) }

    override suspend fun findAll(gameId: Long, kind: ArtworkKind): List<String> {
        val out = internal.findAll(gameId, kind).toMutableList()
        artworkRecordDao.findAll(gameId, kind.name)
            .mapNotNull { it.documentUri.takeIf { uri -> internal.isValidRef(uri) } }
            .forEach { if (it !in out) out += it }
        return out
    }

    override suspend fun deleteAll() {
        internal.deleteAll()

        artworkRecordDao.clear()
    }

    suspend fun saveTempPortable(
        gameId: Long,
        kind: ArtworkKind,
        tempFile: java.io.File,
        source: String,
        userAssigned: Boolean,
        sortOrder: Int = 0,
    ): String? {
        val (tree, game) = portableTarget(gameId) ?: run { tempFile.delete(); return null }
        return persistPortable(tree, game, kind, tempFile, source, userAssigned, sortOrder = sortOrder)
    }

    suspend fun studioInfo(gameId: Long, kind: ArtworkKind, sortOrder: Int = 0): StudioArtworkInfo? {
        val rec = artworkRecordDao.getAt(gameId, kind.name, sortOrder) ?: return null
        return StudioArtworkInfo(
            provider = rec.provider,
            originUrl = rec.originUrl,
            relativePath = rec.relativePath,
            sizeBytes = rec.sizeBytes,
            width = rec.width,
            height = rec.height,
            source = rec.source,
            userAssigned = rec.userAssigned,
            hasPrevious = rec.prevDocumentUri != null && internal.isValidRef(rec.prevDocumentUri),
            hasOriginal = rec.hasOriginal,
            cropRect = rec.cropRect,
            updatedAt = rec.updatedAt,
            sortOrder = rec.sortOrder,
        )
    }

    suspend fun studioAssets(gameId: Long, kind: ArtworkKind): List<StudioArtworkSlot> =
        artworkRecordDao.findAll(gameId, kind.name).map {
            StudioArtworkSlot(
                sortOrder = it.sortOrder,
                documentUri = it.documentUri,
                provider = it.provider,
                originUrl = it.originUrl,
                providerAssetId = it.providerAssetId,
                sizeBytes = it.sizeBytes,
            )
        }

    suspend fun studioAssetsOnDisk(gameId: Long, kind: ArtworkKind): List<StudioArtworkSlot> {
        val slots = studioAssets(gameId, kind)
        if (slots.isEmpty()) return slots
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            slots.filter { internal.isValidRef(it.documentUri) }
        }
    }

    suspend fun nextSortOrder(gameId: Long, kind: ArtworkKind): Int =
        if (!ArtworkFileNaming.supportsMultiple(kind)) 0
        else (artworkRecordDao.maxSortOrder(gameId, kind.name) + 1)
            .coerceAtMost(ArtworkFileNaming.MAX_SORT_ORDER)

    suspend fun studioApplyFromUrl(
        gameId: Long,
        kind: ArtworkKind,
        url: String,
        provider: String?,
        sortOrder: Int = 0,
        providerAssetId: String? = null,
    ): String? {
        val target = portableTarget(gameId) ?: return internal.saveVersionedFromUrl(gameId, kind, url)
        val (tree, game) = target
        val tmp = ArtworkTempIO.downloadToTemp(httpClient, context.cacheDir, kind, url) ?: return null
        return persistPortable(
            tree, game, kind, tmp, source = SOURCE_USER, userAssigned = true,
            originUrl = url, provider = provider, backupPrevious = true,
            sortOrder = sortOrder, providerAssetId = providerAssetId,
        )
    }

    suspend fun studioAppendFromUrl(
        gameId: Long,
        kind: ArtworkKind,
        url: String,
        provider: String?,
        providerAssetId: String? = null,
    ): String? {
        val target = portableTarget(gameId) ?: return internal.saveVersionedFromUrl(gameId, kind, url)
        val (tree, game) = target
        val tmp = ArtworkTempIO.downloadToTemp(httpClient, context.cacheDir, kind, url) ?: return null
        val sortOrder = nextSortOrder(gameId, kind)
        return persistPortable(
            tree, game, kind, tmp, source = SOURCE_USER, userAssigned = true,
            originUrl = url, provider = provider, backupPrevious = true,
            sortOrder = sortOrder, providerAssetId = providerAssetId,
        )
    }

    suspend fun deleteAssetAt(gameId: Long, kind: ArtworkKind, sortOrder: Int): Boolean {
        val rec = artworkRecordDao.getAt(gameId, kind.name, sortOrder) ?: return false
        val target = portableTarget(gameId)
        if (target != null) {
            runCatching { library.deleteUri(Uri.parse(rec.documentUri)) }
            rec.prevDocumentUri?.let { runCatching { library.deleteUri(Uri.parse(it)) } }
        }
        artworkRecordDao.deleteAtAndCompact(gameId, kind.name, sortOrder)
        return true
    }

    suspend fun reorderAssets(gameId: Long, kind: ArtworkKind, orderedSortOrders: List<Int>) {
        val rows = artworkRecordDao.findAll(gameId, kind.name).associateBy { it.sortOrder }
        artworkRecordDao.reorder(gameId, kind.name, orderedSortOrders.mapNotNull { rows[it]?.id })
    }

    suspend fun cropProfileOverride(gameId: Long, kind: ArtworkKind): String? =
        artworkRecordDao.cropProfileKey(gameId, kind.name)

    suspend fun setCropProfileOverride(gameId: Long, kind: ArtworkKind, key: String?) {
        artworkRecordDao.setCropProfileKey(gameId, kind.name, key, System.currentTimeMillis())
    }

    suspend fun studioApplyFromFile(
        gameId: Long, kind: ArtworkKind, tempFile: java.io.File, provider: String?, originUrl: String?,
        sortOrder: Int = 0,
    ): String? {
        val target = portableTarget(gameId) ?: return internal.saveFromFile(gameId, kind, tempFile, sortOrder)
        val (tree, game) = target
        return persistPortable(
            tree, game, kind, tempFile, source = SOURCE_USER, userAssigned = true,
            originUrl = originUrl, provider = provider, backupPrevious = true, sortOrder = sortOrder,
        )
    }

    suspend fun restorePrevious(gameId: Long, kind: ArtworkKind, sortOrder: Int = 0): String? {
        val (tree, game) = portableTarget(gameId) ?: return null
        val rec = artworkRecordDao.getAt(gameId, kind.name, sortOrder) ?: return null
        val prevUri = rec.prevDocumentUri?.let { Uri.parse(it) } ?: return null
        if (!internal.isValidRef(rec.prevDocumentUri)) return null
        val curUri = Uri.parse(rec.documentUri)

        val prevTemp = library.copyUriToTemp(prevUri, context.cacheDir, extSuffix(rec.prevRelativePath)) ?: return null
        val curTemp = if (internal.isValidRef(rec.documentUri))
            library.copyUriToTemp(curUri, context.cacheDir, extSuffix(rec.relativePath)) else null

        val saved = library.saveFromFile(tree, game.platformId, kind, rec.portableName, prevTemp) ?: run {
            curTemp?.delete(); return null
        }

        val newPrev = curTemp?.let {
            val prevExt = extSuffix(rec.relativePath).removePrefix(".")
            library.saveTempIntoPath(
                tree, ArtworkPathResolver.versionsDirSegments(game.platformId, kind),
                "${rec.portableName}.$prevExt", mimeForExt(prevExt), it, deleteTemp = true,
            )
        }
        artworkRecordDao.upsert(
            rec.copy(
                relativePath = ArtworkPathResolver.relativePath(game.platformId, kind, saved.fileName),
                documentUri = saved.uriString,
                sizeBytes = saved.sizeBytes,
                prevDocumentUri = newPrev?.uriString,
                prevRelativePath = newPrev?.let { versionsRelativePath(game.platformId, kind, it.fileName) },
                prevSizeBytes = curTemp?.length() ?: 0L,

                cropRect = null,
                hasOriginal = false,
                updatedAt = System.currentTimeMillis(),
            )
        )
        bustCoil(saved.uriString)
        return saved.uriString
    }

    suspend fun resetToScrapedDefault(gameId: Long, kind: ArtworkKind, sortOrder: Int = 0): String? {
        val rec = artworkRecordDao.getAt(gameId, kind.name, sortOrder) ?: return null
        val url = rec.originUrl ?: return null
        val (tree, game) = portableTarget(gameId) ?: return null
        val tmp = ArtworkTempIO.downloadToTemp(httpClient, context.cacheDir, kind, url) ?: return null

        return persistPortable(
            tree, game, kind, tmp, source = SOURCE_SCRAPE, userAssigned = false,
            originUrl = url, provider = rec.provider, backupPrevious = true, sortOrder = sortOrder,
            providerAssetId = rec.providerAssetId,
        )
    }

    suspend fun clearArtwork(gameId: Long, kind: ArtworkKind): Boolean {
        val target = portableTarget(gameId)
        if (target == null) {
            internal.deleteKind(gameId, kind)
            return true
        }
        val (tree, game) = target

        val records = artworkRecordDao.findAll(gameId, kind.name)
        for (rec in records) {
            runCatching { library.deleteUri(Uri.parse(rec.documentUri)) }
            rec.prevDocumentUri?.let { runCatching { library.deleteUri(Uri.parse(it)) } }
            library.findInPath(tree, ArtworkPathResolver.originalsDirSegments(game.platformId, kind), rec.portableName)
                ?.let { library.deleteUri(it.uri) }
            artworkRecordDao.deleteById(rec.id)
        }
        internal.deleteKind(gameId, kind)
        return records.isNotEmpty()
    }

    suspend fun candidateToTemp(kind: ArtworkKind, url: String): java.io.File? =
        ArtworkTempIO.downloadToTemp(httpClient, context.cacheDir, kind, url)

    suspend fun originalToTemp(gameId: Long, kind: ArtworkKind, sortOrder: Int = 0): java.io.File? {
        val (tree, game) = portableTarget(gameId) ?: return null
        val rec = artworkRecordDao.getAt(gameId, kind.name, sortOrder) ?: return null
        val src = if (rec.hasOriginal) {
            library.findInPath(tree, ArtworkPathResolver.originalsDirSegments(game.platformId, kind), rec.portableName)?.uri
                ?: Uri.parse(rec.documentUri)
        } else {
            Uri.parse(rec.documentUri)
        }
        return library.copyUriToTemp(src, context.cacheDir, extSuffix(rec.relativePath))
    }

    suspend fun saveCropBaked(
        gameId: Long,
        kind: ArtworkKind,
        bakedTempFile: java.io.File,
        cropRect: String,
        sortOrder: Int = 0,
        candidateOriginUrl: String? = null,
        candidateProvider: String? = null,
        candidateAssetId: String? = null,
    ): String? {
        val target = portableTarget(gameId) ?: run { bakedTempFile.delete(); return null }
        val (tree, game) = target
        val rec = artworkRecordDao.getAt(gameId, kind.name, sortOrder)

        if (rec != null && !rec.hasOriginal && internal.isValidRef(rec.documentUri)) {
            val origExt = extSuffix(rec.relativePath).removePrefix(".")
            library.copyUriToTemp(Uri.parse(rec.documentUri), context.cacheDir, ".$origExt")?.let { origTmp ->
                library.saveTempIntoPath(
                    tree, ArtworkPathResolver.originalsDirSegments(game.platformId, kind),
                    "${rec.portableName}.$origExt", mimeForExt(origExt), origTmp, deleteTemp = true,
                )
            }
        }
        return persistPortable(
            tree, game, kind, bakedTempFile, source = rec?.source ?: SOURCE_USER,
            userAssigned = rec?.userAssigned ?: true,
            originUrl = rec?.originUrl ?: candidateOriginUrl,
            provider = rec?.provider ?: candidateProvider,
            backupPrevious = true,
            cropRect = cropRect, hasOriginal = true, sortOrder = sortOrder,
            providerAssetId = rec?.providerAssetId ?: candidateAssetId,
        )
    }

    private suspend fun portableTarget(gameId: Long): Pair<Uri, GameEntity>? {
        val treeUri = folderRepository.getTreeUri() ?: return null
        if (!folderRepository.hasLiveGrant()) return null
        val game = gameDao.getById(gameId) ?: return null
        return Uri.parse(treeUri) to game
    }

    private suspend fun persistPortable(
        tree: Uri,
        game: GameEntity,
        kind: ArtworkKind,
        tempFile: java.io.File,
        source: String,
        userAssigned: Boolean,
        originUrl: String? = null,
        provider: String? = null,
        backupPrevious: Boolean = false,
        cropRect: String? = null,
        hasOriginal: Boolean = false,
        sortOrder: Int = 0,
        providerAssetId: String? = null,
    ): String? {
        val existing = artworkRecordDao.getAt(game.id, kind.name, sortOrder)
        val romFileName = game.romPath?.replace('\\', '/')?.substringAfterLast('/')

        val multi = ArtworkFileNaming.supportsMultiple(kind)
        val slotRecords = if (multi) artworkRecordDao.findAll(game.id, kind.name) else emptyList()
        val establishedBase = if (multi) {
            (existing ?: slotRecords.firstOrNull())?.portableName?.let { ArtworkFileNaming.stripOrdinal(it) }
        } else {
            existing?.portableName
        }
        var portableName = establishedBase
            ?: romFileName?.let { PortableNameResolver.fromRomFileName(it) }
            ?: PortableNameResolver.fromTitle(game.userTitleOverride ?: game.scrapedTitle ?: game.title)
        if (establishedBase == null &&
            artworkRecordDao.findNameCollisions(game.platformId, kind.name, portableName, game.id).isNotEmpty()) {
            portableName = "$portableName (2)"
        }

        if (multi) {
            portableName = existing?.portableName
                ?: ArtworkFileNaming.withOrdinal(portableName, ArtworkFileNaming.nextOrdinal(slotRecords.map { it.portableName }))
        }

        var prevDocumentUri: String? = existing?.prevDocumentUri
        var prevRelativePath: String? = existing?.prevRelativePath
        var prevSizeBytes: Long = existing?.prevSizeBytes ?: 0L
        if (backupPrevious && existing != null && internal.isValidRef(existing.documentUri)) {
            val prevExt = extSuffix(existing.relativePath).removePrefix(".")
            library.copyUriToTemp(Uri.parse(existing.documentUri), context.cacheDir, ".$prevExt")?.let { backupTmp ->
                val stored = library.saveTempIntoPath(
                    tree, ArtworkPathResolver.versionsDirSegments(game.platformId, kind),
                    "$portableName.$prevExt", mimeForExt(prevExt), backupTmp, deleteTemp = true,
                )
                if (stored != null) {
                    prevDocumentUri = stored.uriString
                    prevRelativePath = versionsRelativePath(game.platformId, kind, stored.fileName)
                    prevSizeBytes = existing.sizeBytes
                }
            }
        }

        val saved = library.saveFromFile(tree, game.platformId, kind, portableName, tempFile) ?: return null

        identityRecorder.record(
            tree,
            ArtworkIdentityIndex.Entry(
                platformId = game.platformId,
                kind = kind.name,
                portableName = portableName,
                romCrc32 = game.romCrc32,
                ssId = game.ssId,
                igdbId = game.igdbId,
                sgdbId = game.steamGridDbId,
                artworkKey = game.artworkKey,
            ),
        )
        artworkRecordDao.upsert(
            ArtworkRecordEntity(
                id = existing?.id ?: 0,
                gameId = game.id,
                platformId = game.platformId,
                artworkType = kind.name,
                sortOrder = sortOrder,
                portableName = portableName,
                relativePath = ArtworkPathResolver.relativePath(game.platformId, kind, saved.fileName),
                documentUri = saved.uriString,
                source = source,
                sizeBytes = saved.sizeBytes,
                userAssigned = userAssigned,
                locked = userAssigned,
                originUrl = originUrl ?: existing?.originUrl,
                provider = provider ?: existing?.provider,
                providerAssetId = providerAssetId ?: existing?.providerAssetId,
                prevDocumentUri = prevDocumentUri,
                prevRelativePath = prevRelativePath,
                prevSizeBytes = prevSizeBytes,
                cropRect = cropRect,
                hasOriginal = hasOriginal,
                cropProfileKey = existing?.cropProfileKey,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
        )
        bustCoil(saved.uriString)
        return saved.uriString
    }

    private fun bustCoil(uriString: String) = imageCache.evict(uriString)

    private fun extSuffix(pathOrNull: String?): String {
        val ext = pathOrNull?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() && it.length <= 5 }
        return ".${(ext ?: "jpg").lowercase()}"
    }

    private fun versionsRelativePath(platformId: String, kind: ArtworkKind, fileName: String): String =
        "${ArtworkPathResolver.versionsDirSegments(platformId, kind).joinToString("/")}/$fileName"

    private fun mimeForExt(ext: String): String = when (ext.lowercase()) {
        "png"  -> "image/png"
        "webp" -> "image/webp"
        "pdf"  -> "application/pdf"
        "mp4"  -> "video/mp4"
        "webm" -> "video/webm"
        else   -> "image/jpeg"
    }

    private companion object {
        const val SOURCE_SCRAPE = "scrape"
        const val SOURCE_USER = "user"
    }
}

data class StudioArtworkInfo(
    val provider: String?,
    val originUrl: String?,
    val relativePath: String?,
    val sizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val source: String,
    val userAssigned: Boolean,
    val hasPrevious: Boolean,
    val hasOriginal: Boolean,
    val cropRect: String?,
    val updatedAt: Long,
    val sortOrder: Int = 0,
)

data class StudioArtworkSlot(
    val sortOrder: Int,
    val documentUri: String,
    val provider: String?,
    val originUrl: String?,
    val providerAssetId: String?,
    val sizeBytes: Long,
)
