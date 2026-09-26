package com.psplauncher.feature.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager

interface PcLauncherAdapter {
    val type: PcLauncherType

    val idPrompt: String

    val requiresIntegerId: Boolean

    val sources: List<String>

    fun buildLaunchIntent(packageName: String, gameId: String, source: String?): Intent?
}

private class GameHubFamilyAdapter(
    override val type: PcLauncherType,
    private val generationOf: (String) -> GameHubGeneration,
) : PcLauncherAdapter {
    override val idPrompt =
        "Game ID — on the game's page: tap Copy next to Local Game ID, or ⋮ → Banner Tools → Show Game ID"
    override val requiresIntegerId = false
    override val sources = emptyList<String>()

    override fun buildLaunchIntent(packageName: String, gameId: String, source: String?): Intent? {
        val trimmed = gameId.trim()
        val isLocalId = trimmed.startsWith(LOCAL_ID_PREFIX) && trimmed.length > LOCAL_ID_PREFIX.length
        val numericId = trimmed.toIntOrNull()?.takeIf { it > 0 }
        if (!isLocalId && numericId == null) return null
        val generation = generationOf(packageName)
        val activity = when (generation) {
            GameHubGeneration.V6 -> PcLauncherCatalog.V6_DEEP_LINK_ACTIVITY
            GameHubGeneration.V5 -> PcLauncherCatalog.V5_GAME_DETAIL_ACTIVITY
        }
        return Intent().apply {
            component = ComponentName(packageName, activity)
            action = "$packageName.LAUNCH_GAME"
            when {
                isLocalId -> putExtra("localGameId", trimmed)

                source == "STEAM" -> putExtra("steamAppId", numericId.toString())

                generation == GameHubGeneration.V5 -> {
                    putExtra("steamAppId", numericId.toString())
                    putExtra("localGameId", numericId.toString())
                }
                else -> putExtra("localGameId", numericId.toString())
            }
            putExtra("autoStartGame", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private companion object {
        const val LOCAL_ID_PREFIX = "local_"
    }
}

private class GameNativeAdapter : PcLauncherAdapter {
    override val type = PcLauncherType.GAMENATIVE
    override val idPrompt = "Steam App ID (or store app id) for the installed game"
    override val requiresIntegerId = true
    override val sources = listOf("STEAM", "EPIC", "GOG", "AMAZON")

    override fun buildLaunchIntent(packageName: String, gameId: String, source: String?): Intent? {
        val id = gameId.trim().toIntOrNull()?.takeIf { it > 0 } ?: return null
        return Intent().apply {
            component = ComponentName(packageName, "app.gamenative.MainActivity")
            action = "$packageName.LAUNCH_GAME"
            putExtra("app_id", id)
            putExtra("game_source", source?.takeIf { it.isNotBlank() } ?: "STEAM")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

object PcLauncherAdapters {
    fun forType(type: PcLauncherType, pm: PackageManager? = null): PcLauncherAdapter? = when (type) {
        PcLauncherType.BANNERHUB_V6,
        PcLauncherType.GAMEHUB_LITE -> GameHubFamilyAdapter(type) { pkg ->
            pm?.let { PcLauncherCatalog.gameHubGeneration(pkg, it) } ?: GameHubGeneration.V6
        }
        PcLauncherType.GAMENATIVE   -> GameNativeAdapter()

        PcLauncherType.WINLATOR,
        PcLauncherType.MANUAL       -> null
    }

    internal fun gameHubAdapterFor(
        type: PcLauncherType,
        generationOf: (String) -> GameHubGeneration,
    ): PcLauncherAdapter = GameHubFamilyAdapter(type, generationOf)

    fun gameSourceForExtension(extension: String): String? = when (extension.lowercase()) {
        "steam"  -> "STEAM"
        "epic"   -> "EPIC"
        "gog"    -> "GOG"
        "amazon" -> "AMAZON"
        "pcgame" -> "CUSTOM_GAME"
        else     -> null
    }
}
