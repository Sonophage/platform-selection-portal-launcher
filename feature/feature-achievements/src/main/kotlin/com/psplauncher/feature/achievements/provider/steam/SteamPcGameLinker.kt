package com.psplauncher.feature.achievements.provider.steam

import com.psplauncher.core.domain.achievement.AchievementProvider
import com.psplauncher.feature.achievements.AchievementController
import com.psplauncher.feature.launcher.PcGameAchievementLinker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fulfils the shortcut importer's linker seam: a shortcut whose id carries a certain Steam appid
 * (GameNative `game_<appid>`, an explicit `steamAppId`/`app_id` intent extra) links STEAM at
 * import time — appid equality beats any title ladder.
 */
@Singleton
class SteamPcGameLinker @Inject constructor(
    private val achievements: AchievementController,
) : PcGameAchievementLinker {
    override suspend fun linkSteam(gameId: Long, appId: String) {
        achievements.linkManually(gameId, AchievementProvider.STEAM, appId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PcGameLinkerModule {
    @Binds
    abstract fun bindPcGameAchievementLinker(impl: SteamPcGameLinker): PcGameAchievementLinker
}
