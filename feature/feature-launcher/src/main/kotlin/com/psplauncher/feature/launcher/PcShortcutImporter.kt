package com.psplauncher.feature.launcher

import com.psplauncher.core.domain.model.PlatformIds.WINDOWS as WINDOWS_PLATFORM_ID

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.data.repository.WindowsLibrarySetup
import com.psplauncher.core.data.repository.WindowsSetupState
import com.psplauncher.core.data.model.StorefrontIdentity
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameContentType
import com.psplauncher.core.domain.repository.GameRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class PcShortcutImportResult(
    val gameId: Long,
    val added: Boolean,
    val setup: WindowsSetupState,
) {
    val needsSetup: Boolean get() = setup !is WindowsSetupState.Ready
}

@Singleton
class PcShortcutImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameRepository: GameRepository,
    private val memoryCards: MemoryCardRepository,
    private val windowsLibrary: WindowsLibrarySetup,
) {
    fun isPcLauncher(hostPackage: String?): Boolean =
        PcLauncherCatalog.isVerifiedPcLauncher(hostPackage, context.packageManager)

    suspend fun reconcilePinnedShortcuts(hostPackage: String? = null): Int {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return 0
        val hosts = hostPackage?.let { listOf(it) }
            ?: PcLauncherCatalog.entries.flatMap { it.packageNames }.distinct()

        val pinFlags = LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
            (if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R)
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED_BY_ANY_LAUNCHER else 0)
        var imported = 0
        for (host in hosts.filter { isPcLauncher(it) }) {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(host)
                .setQueryFlags(pinFlags)
            val pinned = runCatching { launcherApps.getShortcuts(query, Process.myUserHandle()) }
                .onFailure { Timber.w(it, "Pinned-shortcut query failed for $host (not the Home app?)") }
                .getOrNull().orEmpty()
            for (shortcut in pinned.filter { it.isEnabled }) {
                importPinnedShortcut(
                    hostPackage = host,
                    shortcutId  = shortcut.id,
                    label       = shortcut.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                        ?: shortcut.longLabel?.toString()?.takeIf { it.isNotBlank() }
                        ?: shortcut.id,
                )
                imported++
            }
        }
        if (imported > 0) Timber.i("Pin reconcile — $imported shortcut(s) imported")
        return imported
    }

    fun watchPinChanges(scope: CoroutineScope) {
        if (watcherRegistered) return
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: return

        scope.launch {
            runCatching { reconcilePinnedShortcuts() }
                .onFailure { Timber.e(it, "Startup pin reconcile failed") }
        }
        val callback = object : LauncherApps.Callback() {
            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<android.content.pm.ShortcutInfo>,
                user: android.os.UserHandle,
            ) {
                if (!isPcLauncher(packageName)) return
                scope.launch {
                    runCatching { reconcilePinnedShortcuts(packageName) }
                        .onFailure { Timber.e(it, "Pin reconcile failed for $packageName") }
                }
            }

            override fun onPackageRemoved(packageName: String?, user: android.os.UserHandle?) = Unit
            override fun onPackageAdded(packageName: String?, user: android.os.UserHandle?) = Unit
            override fun onPackageChanged(packageName: String?, user: android.os.UserHandle?) = Unit
            override fun onPackagesAvailable(p: Array<out String>?, u: android.os.UserHandle?, r: Boolean) = Unit
            override fun onPackagesUnavailable(p: Array<out String>?, u: android.os.UserHandle?, r: Boolean) = Unit
        }
        runCatching {
            launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
            watcherRegistered = true
        }.onFailure { Timber.e(it, "Could not register the pin-change watcher") }
    }

    @Volatile
    private var watcherRegistered = false

    suspend fun importPinnedShortcut(
        hostPackage: String,
        shortcutId: String,
        label: String,
    ): PcShortcutImportResult {
        val existing = gameRepository.getLauncherShortcut(hostPackage, shortcutId)
            ?: titleMatch(label)?.let { match ->

                if (match.shortcutId == null && match.launchIntentUri == null) {
                    gameRepository.attachLauncherHandle(
                        id = match.id,
                        packageName = hostPackage,
                        shortcutId = shortcutId,
                        launchIntentUri = match.launchIntentUri,
                    )
                }
                match
            }
        val gameId = existing?.id ?: gameRepository.upsert(
            Game(
                title         = label,
                platformId    = WINDOWS_PLATFORM_ID,
                packageName   = hostPackage,
                shortcutId    = shortcutId,
                isManualEntry = true,
                contentType   = GameContentType.GAME,
            ),
        )
        gameNativeAppId(hostPackage, shortcutId)?.let { appId ->

            gameRepository.updateStorefrontIdentity(gameId, "STEAM", appId)
        }
        return finish(gameId, added = existing == null, what = "pin \"$label\" from $hostPackage")
    }

    suspend fun importLegacyShortcut(
        hostPackage: String,
        label: String,
        intentUri: String,
    ): PcShortcutImportResult {
        val existing = gameRepository.getByIntentUri(intentUri)
            ?: titleMatch(label)?.let { match ->
                if (match.shortcutId == null && match.launchIntentUri == null) {
                    gameRepository.attachLauncherHandle(
                        id = match.id,
                        packageName = hostPackage,
                        shortcutId = match.shortcutId,
                        launchIntentUri = intentUri,
                    )
                }
                match
            }
        val gameId = existing?.id ?: gameRepository.upsert(
            Game(
                title           = label,
                platformId      = WINDOWS_PLATFORM_ID,
                packageName     = hostPackage,
                launchIntentUri = intentUri,
                isManualEntry   = true,
                contentType     = GameContentType.GAME,
            ),
        )

        StorefrontIdentity.fromLaunchIntentUri(intentUri)?.let { (store, storeId) ->
            gameRepository.updateStorefrontIdentity(gameId, store, storeId)
        }
        return finish(gameId, added = existing == null, what = "legacy shortcut \"$label\" from $hostPackage")
    }

    private suspend fun finish(gameId: Long, added: Boolean, what: String): PcShortcutImportResult {
        val setup = runCatching { windowsLibrary.ensure() }
            .getOrElse { WindowsSetupState.FolderUnavailable }
        if (setup !is WindowsSetupState.Ready) windowsLibrary.flagSetupPrompt()
        runCatching { memoryCards.recountGames(WINDOWS_PLATFORM_ID) }
        Timber.i("PC shortcut import — $what (added=$added, setup=${setup::class.simpleName})")
        return PcShortcutImportResult(gameId, added, setup)
    }

    private suspend fun titleMatch(label: String): Game? {
        val key = normalizeTitle(label)
        return gameRepository.getByPlatform(WINDOWS_PLATFORM_ID)
            .firstOrNull { normalizeTitle(it.displayTitle) == key }
    }

    private companion object {
        val GAME_NATIVE_ID = Regex("""game_(\d{1,12})""")

        fun gameNativeAppId(hostPackage: String, shortcutId: String): String? {
            if (PcLauncherCatalog.forPackage(hostPackage)?.type != PcLauncherType.GAMENATIVE) return null
            return GAME_NATIVE_ID.matchEntire(shortcutId)?.groupValues?.get(1)
        }

        fun steamAppIdFromIntentUri(intentUri: String): String? {
            val intent = runCatching { Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME) }
                .getOrNull() ?: return null
            val appId = intent.getIntExtra("app_id", -1).takeIf { it > 0 }?.toString()
                ?: intent.getStringExtra("steamAppId")?.trim()
            return appId?.takeIf { it.isNotEmpty() && it.length <= 12 && it.all(Char::isDigit) }
        }

        fun normalizeTitle(title: String): String =
            title.lowercase().filter { it.isLetterOrDigit() }
    }
}
