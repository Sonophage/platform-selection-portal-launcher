package com.psplauncher.feature.appbar

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import com.psplauncher.core.common.launch.LaunchTransition
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class HarvestedShortcut(
    val hostPackage: String,
    val shortcutId: String,
    val label: String,
    val longLabel: String?,
)

sealed interface ShortcutHarvestResult {
    data class Success(val shortcuts: List<HarvestedShortcut>) : ShortcutHarvestResult

    object NotDefaultLauncher : ShortcutHarvestResult
    data class Error(val message: String) : ShortcutHarvestResult
}

@Singleton
class LauncherShortcutRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    fun isDefaultLauncher(): Boolean = runCatching {
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, 0)?.activityInfo?.packageName == context.packageName
    }.getOrDefault(false)

    fun homeRoleRequestIntent(): Intent {
        val rm = context.getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) {
            runCatching { return rm.createRequestRoleIntent(RoleManager.ROLE_HOME) }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    suspend fun harvest(hostPackage: String): ShortcutHarvestResult = withContext(Dispatchers.IO) {
        if (!isDefaultLauncher()) return@withContext ShortcutHarvestResult.NotDefaultLauncher

        val query = LauncherApps.ShortcutQuery()
            .setPackage(hostPackage)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )

        runCatching {
            launcherApps.getShortcuts(query, Process.myUserHandle()).orEmpty()
                .filter { it.isEnabled }
                .map { info ->
                    HarvestedShortcut(
                        hostPackage = info.`package`,
                        shortcutId  = info.id,
                        label       = info.shortLabel?.toString()?.takeIf { it.isNotBlank() }
                            ?: info.longLabel?.toString()?.takeIf { it.isNotBlank() }
                            ?: info.id,
                        longLabel   = info.longLabel?.toString(),
                    )
                }
        }.fold(
            onSuccess = { ShortcutHarvestResult.Success(it) },
            onFailure = { e ->
                Timber.e(e, "Failed to harvest shortcuts for $hostPackage")
                if (e is SecurityException) ShortcutHarvestResult.NotDefaultLauncher
                else ShortcutHarvestResult.Error(e.message ?: "Couldn't read shortcuts")
            },
        )
    }

    fun launch(hostPackage: String, shortcutId: String): Result<Unit> = runCatching {
        launcherApps.startShortcut(
            hostPackage, shortcutId, null,
            LaunchTransition.options(context),
            Process.myUserHandle(),
        )
    }.onFailure { Timber.e(it, "startShortcut failed: $hostPackage / $shortcutId") }
}
