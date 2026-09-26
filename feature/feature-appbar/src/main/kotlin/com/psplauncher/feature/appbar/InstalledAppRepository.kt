package com.psplauncher.feature.appbar

import android.content.Context
import android.content.Intent
import com.psplauncher.core.common.launch.LaunchTransition
import com.psplauncher.core.common.launch.LaunchTransition.withoutTransition
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.provider.Settings
import com.psplauncher.core.domain.model.KnownEmulatorPackages
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isGame: Boolean,
    val isEmulator: Boolean,
    val lastUsedAt: Long = 0L,

    val systemCategory: Int = ApplicationInfo.CATEGORY_UNDEFINED,

    val isSystemApp: Boolean = false,

    val gameId: Long? = null,
)

@Singleton
class InstalledAppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: com.psplauncher.core.data.database.dao.GameDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("QueryPermissionsNeeded")
    suspend fun getInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val lastUsedByPackage = loadLastUsedTimestamps()

        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedApps = pm.queryIntentActivities(launchIntent, PackageManager.GET_META_DATA)
            .distinctBy { it.activityInfo?.applicationInfo?.packageName }

        val apps = resolvedApps.mapNotNull { resolveInfo ->
            try {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val packageName = appInfo.packageName

                if (packageName == context.packageName) return@mapNotNull null

                val label = resolveInfo.loadLabel(pm).toString()
                val icon  = resolveInfo.loadIcon(pm)

                val isGame = appInfo.category == ApplicationInfo.CATEGORY_GAME

                val isEmulator = KnownEmulatorPackages.isEmulator(packageName)

                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                    (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                InstalledApp(
                    packageName    = packageName,
                    label          = label,
                    icon           = icon,
                    isGame         = isGame || isEmulator,
                    isEmulator     = isEmulator,
                    lastUsedAt     = lastUsedByPackage[packageName] ?: 0L,
                    systemCategory = appInfo.category,
                    isSystemApp    = isSystem,
                )
            } catch (e: Exception) {
                Timber.w("Failed to load app info: ${e.message}")
                null
            }
        }

        apps.sortedBy { it.label.lowercase() }
            .also { Timber.d("Installed apps loaded: ${it.size} total") }
    }

    fun launchApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Timber.w("No launch intent for $packageName")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).withoutTransition()
        context.startActivity(intent, LaunchTransition.options(context))
        markOpenedOnTheShelf(packageName)
    }

    private fun markOpenedOnTheShelf(packageName: String) {
        scope.launch {
            runCatching {
                gameDao.getAppEntry(packageName)?.let { gameDao.markOpened(it.id, System.currentTimeMillis()) }
            }.onFailure { Timber.w(it, "Could not stamp $packageName on the Last Played shelf") }
        }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false

        @Suppress("DEPRECATION")
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openAppInfo(packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Could not open app info for $packageName") }
    }

    fun uninstallApp(packageName: String) {
        if (packageName == context.packageName) return
        val intent = Intent(
            Intent.ACTION_DELETE,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Could not launch uninstall for $packageName") }
    }

    fun openUsageAccessSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(intent)
        }.onFailure { e ->
            Timber.w(e, "Could not open usage access settings")
        }
    }

    private fun loadLastUsedTimestamps(): Map<String, Long> {
        if (!hasUsageAccess()) return emptyMap()

        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
            ?: return emptyMap()
        val now = System.currentTimeMillis()
        val start = now - TimeUnit.DAYS.toMillis(30)

        return runCatching {
            usageStatsManager
                .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                .orEmpty()
                .groupBy { it.packageName }
                .mapValues { (_, stats) -> stats.maxOf { it.lastTimeUsed } }
                .filterValues { it > 0L }
        }.getOrElse { e ->
            Timber.w(e, "Failed to load usage stats")
            emptyMap()
        }
    }
}
