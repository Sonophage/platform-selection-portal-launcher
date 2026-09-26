package com.psplauncher.feature.settings.pc

import com.psplauncher.core.domain.model.PlatformIds.WINDOWS as WINDOWS_PLATFORM_ID

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.psplauncher.core.common.security.ShortcutIntentSanitizer
import com.psplauncher.core.data.database.dao.ArtworkRecordDao
import com.psplauncher.feature.artwork.api.ArtworkImportManager
import com.psplauncher.feature.artwork.match.MatchProvider
import com.psplauncher.core.data.repository.RomRootRepository
import com.psplauncher.core.data.repository.WindowsLibrarySetup
import com.psplauncher.core.data.repository.WindowsSetupState
import com.psplauncher.core.data.model.StorefrontIdentity
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GameContentType
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.feature.launcher.PcLauncherAdapters
import com.psplauncher.feature.launcher.PcLauncherCatalog
import com.psplauncher.feature.launcher.PcLauncherType
import com.psplauncher.feature.launcher.PcShortcutImporter
import com.psplauncher.feature.library.scanner.PcExportFile
import com.psplauncher.feature.library.scanner.RomScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.psplauncher.feature.library.scanner.cleanRomTitle

data class PcScanReport(
    val setup: WindowsSetupState?,
    val exportsAdded: Int,
    val exportsSkipped: Int,
    val pinsReconciled: Int,
    val message: String,

    val restoredCreated: Int = 0,

    val restoredMatched: Int = 0,

    val restoreSkipped: Int = 0,

    val untrustedExports: Int = 0,

    val artworkClaims: Map<Triple<String, String, String>, Long> = emptyMap(),

    val artworkRelinkedGames: Int? = null,
) {
    val newGames: Int get() = exportsAdded + pinsReconciled + restoredCreated
}

@Singleton
class PcGameScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val windowsLibrarySetup: WindowsLibrarySetup,
    private val pcShortcutImporter: PcShortcutImporter,
    private val romScanner: RomScanner,
    private val gameRepository: GameRepository,
    private val artworkImportManager: ArtworkImportManager,
    private val artworkRecordDao: ArtworkRecordDao,
) {
    suspend fun scan(overrideFolder: Uri? = null): PcScanReport {
        val setup = runCatching { windowsLibrarySetup.ensure() }.getOrNull()
        if (overrideFolder == null && setup is WindowsSetupState.NoRomRoot) {
            return PcScanReport(
                setup, 0, 0, 0,
                message = "Add a ROM Root first — PSP creates <root>/windows/import for exported games.",
            )
        }

        val pm = context.packageManager
        val launchers = installedLaunchers(pm)

        val pins = runCatching { pcShortcutImporter.reconcilePinnedShortcuts() }
            .onFailure { Timber.e(it, "Pin reconcile failed") }
            .getOrDefault(0)

        var added = 0
        var skipped = 0
        val importFolders = if (overrideFolder != null) {
            val treeUri = overrideFolder.toString()
            RomRootRepository.treeDocId(treeUri)?.let { listOf(treeUri to it) } ?: emptyList()
        } else {
            windowsLibrarySetup.importFolders()
        }
        val pfpExports = mutableListOf<PcExportFile>()
        for ((rootUri, importDocId) in importFolders) {
            romScanner.scanPcFolder(rootUri, importDocId).forEach { file ->

                if (file.extension == PcGameExportCodec.EXTENSION) {
                    pfpExports += file
                    return@forEach
                }
                val launch = buildPcLaunch(file, pm, launchers)
                if (launch == null) { skipped++; return@forEach }
                val intentUri = launch.intent.toUri(Intent.URI_INTENT_SCHEME)
                val existing = gameRepository.getByIntentUri(intentUri)
                    ?: findWindowsGame(launch.packageName, file.title)
                if (existing == null) {
                    gameRepository.upsert(
                        Game(

                            title           = cleanRomTitle(file.title),
                            platformId      = WINDOWS_PLATFORM_ID,
                            packageName     = launch.packageName,
                            isManualEntry   = true,
                            contentType     = GameContentType.GAME,
                            launchIntentUri = intentUri,

                            storefront       = launch.storefront,
                            storefrontGameId = launch.storefrontGameId,
                        ),
                    )
                } else {
                    gameRepository.updateStorefrontIdentity(
                        existing.id, launch.storefront, launch.storefrontGameId,
                    )
                }
                added++
            }
        }

        val restore = restoreFromPfpExports(pfpExports, pm)
        val relink = relinkClaimedArtwork(restore.claims, restore.identitySeeds)

        runCatching { windowsLibrarySetup.ensure() }

        val pinNote = if (pins > 0) " $pins pinned shortcut(s) reconciled." else ""
        val restoreNote = buildString {
            if (restore.created + restore.matched > 0) {
                append(" Restored ${restore.created} and matched ${restore.matched} game(s) from .pfpgame files.")
            }
            if (restore.skipped > 0) append(" Skipped ${restore.skipped} .pfpgame file(s): launcher not installed, ambiguous, or pin not in the library.")
            if (restore.untrusted > 0) append(" ${restore.untrusted} .pfpgame file(s) ignored as unreadable or untrusted.")
        }
        val relinkNote = when (relink) {
            ArtworkRelink.NotNeeded -> ""
            is ArtworkRelink.Done -> " Reconnected exported artwork by name (${relink.gamesLinked} game(s) updated)."
            ArtworkRelink.FolderNotLinked -> " Link the artwork folder, then scan again to reconnect exported artwork."
            ArtworkRelink.Failed -> " Reconnecting exported artwork failed — see the log."
        }
        val message = when {
            importFolders.isEmpty() && pins == 0 ->
                "Couldn't read that folder. Pick the folder your launcher exports games into."
            added == 0 && skipped == 0 && pins == 0 && pfpExports.isEmpty() ->
                "No exported PC games found in the selected folder."
            else ->
                "Imported $added PC game(s)" +
                    (if (skipped > 0) ", skipped $skipped (no matching launcher installed)" else "") +
                    "." + restoreNote + relinkNote + pinNote
        }
        Timber.i(
            "PC scan — importFolders=${importFolders.size} added=$added skipped=$skipped pins=$pins " +
                "pfpgame=${pfpExports.size} restored=${restore.created}/${restore.matched} " +
                "restoreSkipped=${restore.skipped} untrusted=${restore.untrusted} claims=${restore.claims.size}",
        )
        return PcScanReport(
            setup, added, skipped, pins, message,
            restoredCreated = restore.created,
            restoredMatched = restore.matched,
            restoreSkipped = restore.skipped,
            untrustedExports = restore.untrusted,
            artworkClaims = restore.claims,
            artworkRelinkedGames = (relink as? ArtworkRelink.Done)?.gamesLinked,
        )
    }

    private sealed interface ArtworkRelink {
        data object NotNeeded : ArtworkRelink
        data object FolderNotLinked : ArtworkRelink
        data object Failed : ArtworkRelink
        data class Done(val gamesLinked: Int) : ArtworkRelink
    }

    private suspend fun relinkClaimedArtwork(
        claims: Map<Triple<String, String, String>, Long>,
        identitySeeds: List<com.psplauncher.feature.artwork.portable.ArtworkIdentityIndex.Entry>,
    ): ArtworkRelink {
        if (claims.isEmpty()) return ArtworkRelink.NotNeeded
        val records = claims.values.toSet().associateWith { artworkRecordDao.getForGame(it) }
        if (PcGameArtworkClaims.unresolved(claims, records).isEmpty()) return ArtworkRelink.NotNeeded
        val result = runCatching { artworkImportManager.relinkLibrary(claims, identitySeeds) }
            .onFailure { Timber.e(it, "Relink after .pfpgame restore failed") }
        return when {
            result.isFailure -> ArtworkRelink.Failed

            result.getOrNull() == null -> ArtworkRelink.FolderNotLinked
            else -> ArtworkRelink.Done(result.getOrNull()!!.gamesLinked)
        }
    }

    private data class PfpRestore(
        val created: Int = 0,
        val matched: Int = 0,
        val skipped: Int = 0,
        val untrusted: Int = 0,
        val claims: Map<Triple<String, String, String>, Long> = emptyMap(),

        val identitySeeds: List<com.psplauncher.feature.artwork.portable.ArtworkIdentityIndex.Entry> = emptyList(),
    )

    private suspend fun restoreFromPfpExports(files: List<PcExportFile>, pm: PackageManager): PfpRestore {
        if (files.isEmpty()) return PfpRestore()
        val games = gameRepository.getByPlatform(WINDOWS_PLATFORM_ID).toMutableList()
        val claims = PcGameArtworkClaims()
        var created = 0
        var matched = 0
        var skipped = 0
        var untrusted = 0
        for (file in files) {
            val export = when (val decoded = PcGameExportCodec.decode(file.idContent.orEmpty())) {
                is PcGameExportDecode.Valid -> decoded.export
                is PcGameExportDecode.Rejected -> {
                    Timber.w("PC scan — ignoring ${file.title}.pfpgame: this export file ${decoded.reason}")
                    untrusted++
                    continue
                }
            }
            val launch = if (export.isPin) null else checkLaunch(export, pm)
            when (val decision = PcGameImportPlanner.plan(export, launch, games)) {
                is PcGameImportDecision.Create -> {
                    val id = gameRepository.upsert(decision.game)
                    games += decision.game.copy(id = id)
                    claims.add(export, id)
                    created++
                }
                is PcGameImportDecision.Fill -> {
                    if (decision.changed) {
                        val original = games.first { it.id == decision.game.id }
                        applyFill(original, decision.game)
                        games.replaceAll { if (it.id == decision.game.id) decision.game else it }
                    }
                    claims.add(export, decision.game.id)
                    matched++
                }
                is PcGameImportDecision.Skip -> {
                    Timber.i("PC scan — skipping ${file.title}.pfpgame: ${decision.reason}")
                    if (decision.reason == PcGameImportSkip.UNTRUSTED_INTENT) untrusted++ else skipped++
                }
            }
        }
        return PfpRestore(created, matched, skipped, untrusted, claims.toMap(), claims.toIdentitySeeds())
    }

    private suspend fun applyFill(original: Game, filled: Game) {
        val id = filled.id
        if (filled.scrapedTitle != original.scrapedTitle) {
            gameRepository.updateScrapedTitle(id, filled.scrapedTitle)
        }
        if (filled.userTitleOverride != original.userTitleOverride) {
            gameRepository.updateUserTitleOverride(id, filled.userTitleOverride)
        }
        if (filled.storefront != original.storefront || filled.storefrontGameId != original.storefrontGameId) {
            gameRepository.updateStorefrontIdentity(id, filled.storefront, filled.storefrontGameId)
        }
        if (filled.ssId != original.ssId) {
            gameRepository.updateProviderMatch(id, MatchProvider.SCREENSCRAPER.name, filled.ssId)
        }
        if (filled.igdbId != original.igdbId) {
            gameRepository.updateProviderMatch(id, MatchProvider.IGDB.name, filled.igdbId)
        }
        if (filled.steamGridDbId != original.steamGridDbId) {
            gameRepository.updateProviderMatch(id, MatchProvider.STEAMGRIDDB.name, filled.steamGridDbId)
        }
    }

    private fun checkLaunch(export: PcGameExport, pm: PackageManager): LaunchCheck {
        val installed = runCatching { pm.getApplicationInfo(export.launcherPackage, 0) }.isSuccess
        val verified = installed && PcLauncherCatalog.isVerifiedPcLauncher(export.launcherPackage, pm)
        val intent = export.launchIntentUri?.let { runCatching { Intent.parseUri(it, Intent.URI_INTENT_SCHEME) }.getOrNull() }
        val sanitized = intent?.let { runCatching { ShortcutIntentSanitizer.sanitize(it, pm) }.getOrNull() }
        return LaunchCheck(
            launcherVerified = verified,
            intentPackage = intent?.component?.packageName ?: intent?.`package`,
            sanitizedIntentUri = sanitized?.toUri(Intent.URI_INTENT_SCHEME),
        )
    }

    data class LauncherExports(
        val files: List<PcExportFile>,
        val intentUris: Set<String>,
    )

    suspend fun launcherExports(): LauncherExports {
        val pm = context.packageManager
        val launchers = installedLaunchers(pm)
        val files = windowsLibrarySetup.importFolders().flatMap { (rootUri, importDocId) ->
            romScanner.scanPcFolder(rootUri, importDocId)
        }.filterNot { it.extension == PcGameExportCodec.EXTENSION }
        val intentUris = files.mapNotNull { file ->
            buildPcLaunch(file, pm, launchers)?.intent?.toUri(Intent.URI_INTENT_SCHEME)
        }.toSet()
        return LauncherExports(files, intentUris)
    }

    private data class InstalledLaunchers(
        val gameNative: String?,
        val gameHub: String?,
        val winlator: String?,
    )

    private fun installedLaunchers(pm: PackageManager): InstalledLaunchers {
        fun installed(vararg pkgs: String) = pkgs.firstOrNull { runCatching { pm.getApplicationInfo(it, 0) }.isSuccess }
        return InstalledLaunchers(
            gameNative = installed("app.gamenative"),

            gameHub = PcLauncherCatalog.installedGameHubFamilyPackages(pm).firstOrNull(),
            winlator = installed("com.winlator", "com.winlator.cmod"),
        )
    }

    private data class PcLaunch(
        val intent: Intent,
        val launcherName: String,
        val packageName: String,
        val storefront: String? = null,
        val storefrontGameId: String? = null,
    )

    private fun buildPcLaunch(
        file: PcExportFile,
        pm: PackageManager,
        launchers: InstalledLaunchers,
    ): PcLaunch? {
        val gameNativePkg = launchers.gameNative
        val gameHubPkg = launchers.gameHub
        val winlatorPkg = launchers.winlator
        if (file.extension == "desktop") {
            val path = file.rawPath ?: return null
            val pkg  = winlatorPkg ?: return null
            val intent = pm.getLaunchIntentForPackage(pkg)?.apply {
                putExtra("shortcut_path", path)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            } ?: return null

            return PcLaunch(intent, "Winlator", pkg)
        }

        val id = file.idContent?.trim()?.takeIf { it.toIntOrNull()?.let { n -> n > 0 } == true } ?: return null
        val source = PcLauncherAdapters.gameSourceForExtension(file.extension) ?: return null
        val storefront = StorefrontIdentity.normalizeStore(source)
        val storefrontId = id.takeIf { StorefrontIdentity.isPlausibleAppId(it) }

        gameNativePkg?.let { pkg ->
            val intent = PcLauncherAdapters.forType(PcLauncherType.GAMENATIVE)?.buildLaunchIntent(pkg, id, source) ?: return null
            return PcLaunch(intent, "GameNative", pkg, storefront, storefrontId)
        }

        if (file.extension == "steam" && gameHubPkg != null) {
            val type = if (gameHubPkg == "gamehub.lite") PcLauncherType.GAMEHUB_LITE else PcLauncherType.BANNERHUB_V6
            val name = if (gameHubPkg == "gamehub.lite") "GameHub Lite" else "BannerHub"
            val intent = PcLauncherAdapters.forType(type, pm)?.buildLaunchIntent(gameHubPkg, id, "STEAM") ?: return null
            return PcLaunch(intent, name, gameHubPkg, storefront = "STEAM", storefrontGameId = storefrontId)
        }
        return null
    }

    private suspend fun findWindowsGame(packageName: String, title: String): Game? {
        val key = WindowsGameKeys.normalizeTitle(title)
        return gameRepository.getByPlatform(WINDOWS_PLATFORM_ID).firstOrNull {
            it.packageName == packageName && WindowsGameKeys.normalizeTitle(it.displayTitle) == key
        }
    }

    private companion object {
    }
}
