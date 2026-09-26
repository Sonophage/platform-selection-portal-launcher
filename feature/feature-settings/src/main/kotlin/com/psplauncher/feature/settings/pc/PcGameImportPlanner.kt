package com.psplauncher.feature.settings.pc

import com.psplauncher.core.domain.model.PlatformIds.WINDOWS as WINDOWS_PLATFORM_ID

import com.psplauncher.core.data.database.entity.ArtworkRecordEntity
import com.psplauncher.core.data.model.StorefrontIdentity
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameContentType
import com.psplauncher.feature.artwork.portable.ArtworkIdentityIndex
import com.psplauncher.feature.launcher.PcLauncherCatalog

internal object WindowsGameKeys {
    private const val GAMEHUB_FAMILY = "gamehub-family"

    fun storefrontPairOf(game: Game): Pair<String, String>? {
        val store = game.storefront
        val id = game.storefrontGameId
        if (store != null && id != null) return store to id
        return StorefrontIdentity.fromLaunchIntentUri(game.launchIntentUri)
    }

    fun storefrontPairOf(export: PcGameExport): Pair<String, String>? {
        val store = StorefrontIdentity.normalizeStore(export.storefront)
        val id = export.storefrontGameId
        if (store != null && id != null) return store to id
        return StorefrontIdentity.fromLaunchIntentUri(export.launchIntentUri)
    }

    fun launcherKey(packageName: String?): String? {
        val pkg = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (PcLauncherCatalog.isGameHubFamilyPackage(pkg)) GAMEHUB_FAMILY else pkg
    }

    fun normalizeTitle(title: String): String = title.lowercase().filter { it.isLetterOrDigit() }
}

data class LaunchCheck(

    val launcherVerified: Boolean,

    val intentPackage: String?,

    val sanitizedIntentUri: String?,
)

enum class PcGameImportSkip {
    LAUNCHER_UNAVAILABLE,

    UNTRUSTED_INTENT,

    AMBIGUOUS,

    PIN_NOT_IN_LIBRARY,
}

sealed interface PcGameImportDecision {
    data class Create(val game: Game) : PcGameImportDecision

    data class Fill(val game: Game, val changed: Boolean) : PcGameImportDecision

    data class Skip(val reason: PcGameImportSkip) : PcGameImportDecision
}

object PcGameImportPlanner {
    fun plan(export: PcGameExport, launch: LaunchCheck?, windowsGames: List<Game>): PcGameImportDecision {
        val games = windowsGames.filter { it.platformId == WINDOWS_PLATFORM_ID }

        if (export.isPin) {
            val match = games.firstOrNull { it.packageName == export.launcherPackage && it.shortcutId == export.shortcutId }
                ?: when (val byTitle = byLauncherAndTitle(export, games)) {
                    is TitleMatch.Unique -> byTitle.game
                    TitleMatch.Ambiguous -> return PcGameImportDecision.Skip(PcGameImportSkip.AMBIGUOUS)
                    TitleMatch.None -> return PcGameImportDecision.Skip(PcGameImportSkip.PIN_NOT_IN_LIBRARY)
                }
            return fill(match, export)
        }

        if (launch == null || !launch.launcherVerified) return PcGameImportDecision.Skip(PcGameImportSkip.LAUNCHER_UNAVAILABLE)
        val sanitized = launch.sanitizedIntentUri
        if (sanitized == null || launch.intentPackage != export.launcherPackage) {
            return PcGameImportDecision.Skip(PcGameImportSkip.UNTRUSTED_INTENT)
        }

        val intentUris = setOfNotNull(export.launchIntentUri, sanitized)
        games.firstOrNull { it.launchIntentUri in intentUris }?.let { return fill(it, export) }

        WindowsGameKeys.storefrontPairOf(export)?.let { pair ->
            games.firstOrNull { WindowsGameKeys.storefrontPairOf(it) == pair }?.let { return fill(it, export) }
        }

        return when (val byTitle = byLauncherAndTitle(export, games)) {
            is TitleMatch.Unique -> fill(byTitle.game, export)
            TitleMatch.Ambiguous -> PcGameImportDecision.Skip(PcGameImportSkip.AMBIGUOUS)
            TitleMatch.None -> PcGameImportDecision.Create(create(export, sanitized))
        }
    }

    private sealed interface TitleMatch {
        data class Unique(val game: Game) : TitleMatch
        data object Ambiguous : TitleMatch
        data object None : TitleMatch
    }

    private fun byLauncherAndTitle(export: PcGameExport, games: List<Game>): TitleMatch {
        val launcher = WindowsGameKeys.launcherKey(export.launcherPackage)
        val wanted = titleKeys(export.title, export.scrapedTitle, export.userTitleOverride)
        if (launcher == null || wanted.isEmpty()) return TitleMatch.None
        val fits = games.filter { game ->
            WindowsGameKeys.launcherKey(game.packageName) == launcher &&
                titleKeys(game.displayTitle, game.title, game.scrapedTitle).any { it in wanted }
        }
        return when (fits.size) {
            0 -> TitleMatch.None
            1 -> TitleMatch.Unique(fits.single())
            else -> TitleMatch.Ambiguous
        }
    }

    private fun titleKeys(vararg titles: String?): Set<String> =
        titles.mapNotNull { it?.let(WindowsGameKeys::normalizeTitle)?.takeIf(String::isNotEmpty) }.toSet()

    private fun fill(game: Game, export: PcGameExport): PcGameImportDecision.Fill {
        val takeStorefront = game.storefront == null && game.storefrontGameId == null &&
            export.storefront != null && export.storefrontGameId != null
        val filled = game.copy(
            scrapedTitle = game.scrapedTitle ?: export.scrapedTitle,
            userTitleOverride = game.userTitleOverride ?: export.userTitleOverride,
            storefront = if (takeStorefront) export.storefront else game.storefront,
            storefrontGameId = if (takeStorefront) export.storefrontGameId else game.storefrontGameId,
            ssId = game.ssId ?: export.ssId,
            igdbId = game.igdbId ?: export.igdbId,
            steamGridDbId = game.steamGridDbId ?: export.steamGridDbId,
        )
        return PcGameImportDecision.Fill(filled, changed = filled != game)
    }

    private fun create(export: PcGameExport, sanitizedIntentUri: String): Game {
        val hasStorefront = export.storefront != null && export.storefrontGameId != null
        return Game(
            title = export.title,
            platformId = WINDOWS_PLATFORM_ID,
            packageName = export.launcherPackage,
            isManualEntry = true,
            contentType = GameContentType.GAME,

            launchIntentUri = sanitizedIntentUri,
            scrapedTitle = export.scrapedTitle,
            userTitleOverride = export.userTitleOverride,
            storefront = if (hasStorefront) export.storefront else null,
            storefrontGameId = if (hasStorefront) export.storefrontGameId else null,
            ssId = export.ssId,
            igdbId = export.igdbId,
            steamGridDbId = export.steamGridDbId,
        )
    }
}

class PcGameArtworkClaims {
    private val owners = HashMap<Triple<String, String, String>, Long>()
    private val contested = HashSet<Triple<String, String, String>>()

    private val seeds = HashMap<Triple<String, String, String>, ArtworkIdentityIndex.Entry>()

    fun add(export: PcGameExport, gameId: Long) {
        export.artwork.forEach { item ->
            val key = Triple(WINDOWS_PLATFORM_ID, item.kind, item.portableName.lowercase())
            if (key in contested) return@forEach
            val owner = owners.putIfAbsent(key, gameId)
            if (owner != null && owner != gameId) {
                owners.remove(key)
                seeds.remove(key)
                contested.add(key)
            } else {
                seeds[key] = ArtworkIdentityIndex.Entry(
                    platformId = WINDOWS_PLATFORM_ID,
                    kind = item.kind,
                    portableName = item.portableName,
                    ssId = export.ssId,
                    igdbId = export.igdbId,
                    sgdbId = export.steamGridDbId,
                )
            }
        }
    }

    fun toMap(): Map<Triple<String, String, String>, Long> = owners.toMap()

    fun toIdentitySeeds(): List<ArtworkIdentityIndex.Entry> =
        seeds.values.filter { it.tokens().isNotEmpty() }

    companion object {
        fun unresolved(
            claims: Map<Triple<String, String, String>, Long>,
            recordsByGame: Map<Long, List<ArtworkRecordEntity>>,
        ): Map<Triple<String, String, String>, Long> = claims.filter { (key, gameId) ->
            recordsByGame[gameId].orEmpty().none { record ->
                record.artworkType == key.second && record.portableName.lowercase() == key.third
            }
        }
    }
}
