package com.psplauncher.core.data.repository

import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.dao.MemoryCardDao
import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.database.entity.toDomain
import com.psplauncher.core.data.database.entity.toEntity
import com.psplauncher.core.domain.model.MemoryCard
import com.psplauncher.core.domain.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryCardRepository @Inject constructor(
    private val memoryCardDao: MemoryCardDao,
    private val platformDao: PlatformDao,
    private val gameDao: GameDao,
) {
    fun observeAll(): Flow<List<MemoryCard>> =
        memoryCardDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeEnabled(): Flow<List<MemoryCard>> =
        memoryCardDao.observeEnabled().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<MemoryCard> = memoryCardDao.getAll().map { it.toDomain() }

    suspend fun getById(platformId: String): MemoryCard? =
        memoryCardDao.getById(platformId)?.toDomain()

    suspend fun availablePlatformCatalog(): List<Platform> =
        platformDao.observeAll().first().map { it.toDomain() }

    suspend fun unconfiguredPlatforms(): List<Platform> {
        val configured = memoryCardDao.getAll().map { it.platformId }.toSet()
        return availablePlatformCatalog().filter { it.id !in configured }
    }

    suspend fun addCard(
        platformId: String,
        displayName: String,
        romDirectory: String?,
        emulatorId: String?,
        extensions: List<String>? = null,
        scanRecursively: Boolean = true,
    ): MemoryCard {
        val platform = platformDao.getById(platformId)
        val exts = (extensions ?: platform?.romExtensions
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList())
        val card = MemoryCard(
            platformId          = platformId,
            displayName         = displayName,
            romDirectory        = romDirectory,
            emulatorId          = emulatorId,
            supportedExtensions = exts,
            scanRecursively     = scanRecursively,
            sortOrder           = memoryCardDao.maxSortOrder() + 1,
        )
        memoryCardDao.upsert(card.toEntity())
        Timber.i("Memory Card created: $platformId dir=$romDirectory exts=$exts")
        return card
    }

    suspend fun remove(platformId: String) {
        gameDao.deleteByPlatform(platformId)
        memoryCardDao.delete(platformId)
        Timber.i("Memory Card removed and games cleared: $platformId")
    }

    suspend fun setEnabled(platformId: String, enabled: Boolean) =
        memoryCardDao.setEnabled(platformId, enabled)

    suspend fun setPinned(platformId: String, pinned: Boolean) =
        memoryCardDao.setPinned(platformId, pinned)

    suspend fun rename(platformId: String, name: String) =
        memoryCardDao.setDisplayName(platformId, name)

    suspend fun setRomDirectory(platformId: String, dir: String?) =
        memoryCardDao.setRomDirectory(platformId, dir)

    suspend fun setSafFolder(platformId: String, treeUri: String, derivedPath: String?) =
        memoryCardDao.setSafFolder(platformId, treeUri, derivedPath)

    suspend fun setEmulator(platformId: String, emulatorId: String?) =
        memoryCardDao.setEmulator(platformId, emulatorId)

    suspend fun setExtensions(platformId: String, extensions: List<String>) {
        val normalized = extensions
            .map { it.trim().lowercase().removePrefix(".") }
            .filter { it.isNotBlank() }
            .distinct()
        memoryCardDao.setSupportedExtensions(platformId, normalized.joinToString(","))
        Timber.i("Memory Card $platformId extensions set: $normalized")
    }

    suspend fun recountGames(platformId: String) {
        memoryCardDao.updateGameCount(platformId, gameDao.countGamesByPlatform(platformId))
    }

    suspend fun recordScan(platformId: String, scannedAt: Long) {
        memoryCardDao.updateScanResult(platformId, scannedAt, gameDao.countGamesByPlatform(platformId))
    }

    suspend fun move(platformId: String, up: Boolean): Boolean {
        val ordered = memoryCardDao.getAll()
        val index = ordered.indexOfFirst { it.platformId == platformId }
        if (index < 0) return false
        val targetIndex = if (up) index - 1 else index + 1
        if (targetIndex !in ordered.indices) return false

        val current = ordered[index]
        val target  = ordered[targetIndex]

        if (current.pinned != target.pinned) return false

        memoryCardDao.setSortOrder(current.platformId, target.sortOrder)
        memoryCardDao.setSortOrder(target.platformId, current.sortOrder)
        return true
    }
}
