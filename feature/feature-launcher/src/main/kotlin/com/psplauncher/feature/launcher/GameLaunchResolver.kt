package com.psplauncher.feature.launcher

import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.database.entity.PlatformEntity
import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.domain.model.Game
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameLaunchResolver @Inject constructor(
    private val profileRepository: EmulatorProfileRepository,
    private val memoryCardRepository: MemoryCardRepository,
    private val platformDao: PlatformDao,
) {
    suspend fun resolve(game: Game, platform: PlatformEntity? = null): Result<ResolvedLaunch> {
        val platformId = game.platformId
        return EmulatorLaunchResolver.resolve(
            platformId           = platformId,
            installedProfiles    = profileRepository.getInstalledProfiles(),

            platformProfiles     = profileRepository.getProfilesForPlatform(platformId),
            perGameOverride      = game.emulatorPackage?.takeIf { it.isNotBlank() },
            memoryCardEmulatorId = memoryCardRepository.getById(platformId)?.emulatorId?.takeIf { it.isNotBlank() },
            platformDefault      = (platform?.preferredEmulatorPackage
                ?: platformDao.getById(platformId)?.preferredEmulatorPackage)?.takeIf { it.isNotBlank() },
        )
    }
}
