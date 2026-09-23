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
    val icon: Drawable,
    val isGame: Boolean,
    val isEmulator: Boolean,
    val lastUsedAt: Long = 0L,
    // ApplicationInfo.category (CATEGORY_VIDEO, CATEGORY_AUDIO, …) or -1 when undefined.
    val systemCategory: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    // True for pre-installed system apps. Used as a guard rail: uninstall isn't offered for these
    // (Android would reject it anyway), only "App Info".
    val isSystemApp: Boolean = false,
)

@Singleton
class InstalledAppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: com.psplauncher.core.data.database.dao.GameDao,
) {
    // Fire-and-forget writes that must outlive the caller: a launch stamp is written as the
    // launcher is being covered by the app it just started, and the ViewModel that asked for the
    // launch may well be gone by the time the row is updated.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val lastUsedByPackage = loadLastUsedTimestamps()

        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        // One ResolveInfo comes back per launcher ACTIVITY, and a package may declare more than
        // one (the AYN Thor's dual-screen keyboard does). The app model is package-level all the
        // way down — selection, launching and lazy-list keys are all packageName — so a second
        // entry is a duplicate that crashes the pickers. Collapse per package, as the music and
        // video intent resolvers already do.
        val resolvedApps = pm.queryIntentActivities(launchIntent, PackageManager.GET_META_DATA)
            .distinctBy { it.activityInfo?.applicationInfo?.packageName }

        val apps = resolvedApps.mapNotNull { resolveInfo ->
            try {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val packageName = appInfo.packageName

                // Skip ourselves
                if (packageName == context.packageName) return@mapNotNull null

                val label = resolveInfo.loadLabel(pm).toString()
                val icon  = resolveInfo.loadIcon(pm)

                // FLAG_IS_GAME was the pre-API-26 way of saying this and is deprecated in
                // favour of `category`, which the same check already reads. minSdk is 29, so
                // every device here reports the category and the flag bit adds nothing but a
                // warning.
                val isGame = appInfo.category == ApplicationInfo.CATEGORY_GAME

                val isEmulator = KnownEmulatorPackages.isEmulator(packageName)
                // A system app that has NOT been updated by the user can't be uninstalled; treat
                // updated system apps (Chrome, etc.) as uninstallable.
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

    /**
     * Moves an app to the front of the Last Played shelf.
     *
     * HERE, and not at the call sites, because this is the one funnel every app launch passes
     * through — the drawer, the XMB row, App Detail and the storefront drawer all end up on the
     * line above. Nothing wrote this stamp at all before, so the shelf simply never reordered
     * when you went back to an app: apps kept whatever position their first launch gave them,
     * which on a shelf whose entire meaning is recency reads as the list being stuck.
     *
     * Games are not stamped here and must not be: LaunchDispatcher writes theirs once the
     * emulator has demonstrably covered the launcher and the user has come back, which is a
     * stronger claim than this one and comes with a duration. An app has no hand-off to verify —
     * `startActivity` on a launcher intent either worked or threw — so "opened, now" is the whole
     * of what is known, and the shelf only ever asked for that.
     *
     * Silent when the package is not in the library. An app opened from All Apps that was never
     * added is not on the shelf, and putting it there would be a different feature.
     */
    private fun markOpenedOnTheShelf(packageName: String) {
        scope.launch {
            runCatching {
                gameDao.getAppEntry(packageName)?.let { gameDao.markOpened(it.id, System.currentTimeMillis()) }
            }.onFailure { Timber.w(it, "Could not stamp $packageName on the Last Played shelf") }
        }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        // unsafeCheckOpNoThrow is deprecated in favour of the attribution-tag overload, which
        // needs API 30; minSdk is 29, so this is the newest call every supported device has.
        // Kept deliberately, with the warning suppressed so a real deprecation is not lost in it.
        @Suppress("DEPRECATION")
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Opens the system App Info (details & permissions) page for [packageName]. */
    fun openAppInfo(packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Timber.w(it, "Could not open app info for $packageName") }
    }

    /** Launches the system uninstall flow for [packageName]. Android shows its own confirmation
     *  dialog, so this is only ever fired after the in-app guard-rail confirmation. */
    fun uninstallApp(packageName: String) {
        if (packageName == context.packageName) return   // never offer to uninstall ourselves
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
