package com.psplauncher.feature.launcher

import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.database.entity.PlatformEntity
import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.domain.model.Game
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Which emulator runs a game. The one place that answers it.
 *
 * [EmulatorLaunchResolver] holds the ladder — per-game override, memory card, platform default,
 * then the automatic pick — but it is a pure function and cannot fetch its own rungs. Assembling
 * those three stored values is the part a caller can get wrong, and one did: the XMB's direct
 * launch read only `getProfilesForPlatform(...).firstOrNull { it.isAvailable }`, which is the
 * BOTTOM rung on its own. With direct launch on — the path confirm actually takes — a game pinned
 * through "Change Emulator", a Memory Card's emulator and the per-system default were all written
 * and then ignored, and the refusal even named a setting that path never read.
 *
 * So the assembly lives here and both callers take it whole. A launch resolved anywhere else is
 * a second ladder, and the difference between two ladders is invisible until someone's emulator
 * choice quietly stops happening.
 */
@Singleton
class GameLaunchResolver @Inject constructor(
    private val profileRepository: EmulatorProfileRepository,
    private val memoryCardRepository: MemoryCardRepository,
    private val platformDao: PlatformDao,
) {

    /**
     * The profile that should run [game], or the reason none can.
     *
     * [platform] is an optional row the caller already has in hand; it is only read for its
     * preferred emulator, and the dao is asked when it is absent. Passing a stale one is the
     * caller's own business — it is the same row the dao would return.
     */
    suspend fun resolve(game: Game, platform: PlatformEntity? = null): Result<ResolvedLaunch> {
        val platformId = game.platformId
        return EmulatorLaunchResolver.resolve(
            platformId           = platformId,
            installedProfiles    = profileRepository.getInstalledProfiles(),
            // Already filtered to available-and-supported, ordered by launch preference and
            // core-stabilised. The ladder's bottom rung takes its first entry.
            platformProfiles     = profileRepository.getProfilesForPlatform(platformId),
            perGameOverride      = game.emulatorPackage?.takeIf { it.isNotBlank() },
            memoryCardEmulatorId = memoryCardRepository.getById(platformId)?.emulatorId?.takeIf { it.isNotBlank() },
            platformDefault      = (platform?.preferredEmulatorPackage
                ?: platformDao.getById(platformId)?.preferredEmulatorPackage)?.takeIf { it.isNotBlank() },
        )
    }
}
