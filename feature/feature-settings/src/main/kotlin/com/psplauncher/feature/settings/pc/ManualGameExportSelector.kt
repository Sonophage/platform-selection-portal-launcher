package com.psplauncher.feature.settings.pc

import com.psplauncher.core.domain.model.PlatformIds.WINDOWS as WINDOWS_PLATFORM_ID

import com.psplauncher.core.data.model.IntentUriExtras
import com.psplauncher.core.data.model.StorefrontIdentity
import com.psplauncher.core.domain.model.Game
import com.psplauncher.feature.launcher.PcLauncherAdapters
import com.psplauncher.feature.library.scanner.PcExportFile

data class ManualGameExportSelection(
    val exported: List<Game>,
    val skipped: Int,
)

object ManualGameExportSelector {
    fun select(
        windowsGames: List<Game>,
        gamesWithArtwork: Set<Long>,
        launcherFiles: List<PcExportFile>,
        reproducedIntentUris: Set<String>,
    ): ManualGameExportSelection {
        val filePairs = launcherFiles.mapNotNull(::storefrontPairOf).toSet()
        val desktopPaths = launcherFiles.filter { it.extension == "desktop" }.mapNotNull { it.rawPath }.toSet()

        val exported = windowsGames.filter { game ->
            when {
                game.platformId != WINDOWS_PLATFORM_ID -> false

                game.shortcutId != null -> game.id in gamesWithArtwork
                else -> {
                    val intentUri = game.launchIntentUri ?: return@filter false
                    val recreatedByFile = intentUri in reproducedIntentUris ||
                        WindowsGameKeys.storefrontPairOf(game)?.let { it in filePairs } == true ||
                        IntentUriExtras.stringExtra(intentUri, "shortcut_path")?.let { it in desktopPaths } == true
                    !recreatedByFile
                }
            }
        }
        return ManualGameExportSelection(exported, skipped = windowsGames.size - exported.size)
    }

    private fun storefrontPairOf(file: PcExportFile): Pair<String, String>? {
        val store = StorefrontIdentity.normalizeStore(PcLauncherAdapters.gameSourceForExtension(file.extension))
            ?: return null
        val id = file.idContent?.trim()?.takeIf { StorefrontIdentity.isPlausibleAppId(it) } ?: return null
        return store to id
    }
}
