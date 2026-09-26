package com.psplauncher.feature.launcher

import android.content.ComponentName
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

enum class PcLauncherType { WINLATOR, GAMEHUB_LITE, BANNERHUB_V6, GAMENATIVE, MANUAL }

enum class GameHubGeneration { V5, V6 }

data class PcLauncherDef(
    val type: PcLauncherType,
    val displayName: String,

    val packageNames: List<String>,
)

object PcLauncherCatalog {
    private val GAMEHUB_FAMILY_PACKAGES = listOf(
        "gamehub.lite",
        "banner.hub",
        "com.xiaoji.egggame",
        "com.antutu.ABenchMark",
        "com.antutu.benchmark.full",
        "com.ludashi.aibench",
        "com.tencent.ig",
        "com.miHoYo.GenshinImpact",
        "com.tencent.tmgp.cf",
    )

    const val V6_DEEP_LINK_ACTIVITY = "com.xiaoji.egggame.DeepLinkActivity"
    const val V5_GAME_DETAIL_ACTIVITY = "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity"
    private const val V5_ROUTER_ACTIVITY = "com.xj.app.DeepLinkRouterActivity"

    val entries: List<PcLauncherDef> = listOf(
        PcLauncherDef(PcLauncherType.BANNERHUB_V6, "BannerHub",    GAMEHUB_FAMILY_PACKAGES),
        PcLauncherDef(PcLauncherType.GAMEHUB_LITE, "GameHub Lite", GAMEHUB_FAMILY_PACKAGES),
        PcLauncherDef(PcLauncherType.WINLATOR,     "Winlator",     listOf("com.winlator", "com.winlator.cmod")),
        PcLauncherDef(PcLauncherType.GAMENATIVE,   "GameNative",   listOf("app.gamenative")),
    )

    private val byPackage: Map<String, PcLauncherDef> =
        entries.flatMap { def -> def.packageNames.map { it to def } }.toMap()

    fun forPackage(packageName: String?): PcLauncherDef? = packageName?.let { byPackage[it] }

    fun isGameHubFamilyPackage(packageName: String?): Boolean =
        packageName in GAMEHUB_FAMILY_PACKAGES

    private data class CachedFingerprint(val lastUpdateTime: Long, val generation: GameHubGeneration?)

    private val fingerprints = ConcurrentHashMap<String, CachedFingerprint>()

    fun gameHubGeneration(packageName: String, pm: PackageManager): GameHubGeneration? {
        val info = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull() ?: return null
        fingerprints[packageName]
            ?.takeIf { it.lastUpdateTime == info.lastUpdateTime }
            ?.let { return it.generation }
        val generation = resolveGeneration(
            versionNameMajor = info.versionName?.substringBefore('.')?.toIntOrNull(),
            label            = applicationLabel(packageName, pm),
            hasClass         = { cls -> hasActivity(packageName, cls, pm) },
        )
        fingerprints[packageName] = CachedFingerprint(info.lastUpdateTime, generation)
        return generation
    }

    internal fun resolveGeneration(
        versionNameMajor: Int?,
        label: String?,
        hasClass: (String) -> Boolean,
    ): GameHubGeneration? = when {
        hasClass(V6_DEEP_LINK_ACTIVITY) -> GameHubGeneration.V6
        hasClass(V5_GAME_DETAIL_ACTIVITY) || hasClass(V5_ROUTER_ACTIVITY) -> GameHubGeneration.V5
        labelNamesFamilyLauncher(label) ->
            if ((versionNameMajor ?: 0) >= 6) GameHubGeneration.V6 else GameHubGeneration.V5
        else -> null
    }

    fun verifiedInstalledPackage(def: PcLauncherDef, pm: PackageManager): String? =
        def.packageNames.firstOrNull { pkg -> verifiesAs(def.type, pkg, pm) }

    fun isVerifiedPcLauncher(packageName: String?, pm: PackageManager): Boolean {
        val pkg = packageName ?: return false
        if (forPackage(pkg) == null) return false
        return if (pkg in GAMEHUB_FAMILY_PACKAGES) gameHubGeneration(pkg, pm) != null else true
    }

    fun installedGameHubFamilyPackages(pm: PackageManager): List<String> =
        GAMEHUB_FAMILY_PACKAGES.filter { gameHubGeneration(it, pm) != null }

    private fun verifiesAs(type: PcLauncherType, pkg: String, pm: PackageManager): Boolean {
        if (pkg !in GAMEHUB_FAMILY_PACKAGES) return isInstalled(pkg, pm)
        if (gameHubGeneration(pkg, pm) == null) return false
        return brandMatches(type, applicationLabel(pkg, pm))
    }

    internal fun brandMatches(type: PcLauncherType, label: String?): Boolean {
        val isBanner = label?.contains("banner", ignoreCase = true) == true
        return when (type) {
            PcLauncherType.BANNERHUB_V6 -> isBanner
            PcLauncherType.GAMEHUB_LITE -> !isBanner
            else                        -> true
        }
    }

    private fun labelNamesFamilyLauncher(label: String?): Boolean =
        label != null && (
            label.contains("gamehub", ignoreCase = true) ||
                label.contains("game hub", ignoreCase = true) ||
                label.contains("banner", ignoreCase = true)
            )

    private fun isInstalled(pkg: String, pm: PackageManager): Boolean =
        runCatching { pm.getApplicationInfo(pkg, 0) }.isSuccess

    private fun applicationLabel(pkg: String, pm: PackageManager): String? = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrNull()

    private fun hasActivity(pkg: String, cls: String, pm: PackageManager): Boolean =
        runCatching { pm.getActivityInfo(ComponentName(pkg, cls), 0) }.isSuccess
}
