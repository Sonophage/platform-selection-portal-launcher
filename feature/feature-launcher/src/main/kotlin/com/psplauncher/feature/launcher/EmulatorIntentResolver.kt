package com.psplauncher.feature.launcher

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.net.Uri
import android.os.Build
import com.psplauncher.core.domain.model.EmulatorProfile
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.IntentType
import com.psplauncher.core.domain.model.LaunchTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmulatorIntentResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val romUriMinter: RomUriMinter,
) {
    suspend fun resolve(game: Game, profile: EmulatorProfile): Result<Intent> {
        return runCatching {
            validateBeforeLaunch(game, profile)
            val intent = when (profile.intentType) {
                IntentType.ACTION_VIEW    -> buildViewIntent(game, profile)
                IntentType.COMPONENT      -> buildComponentIntent(game, profile)
                IntentType.CUSTOM_COMMAND -> buildCustomCommandIntent(game, profile)
                IntentType.SHORTCUT       -> error("Shortcut launch not supported from this screen")
            }
            Timber.d(
                "Launch intent resolved: gameId=${game.id}, title=${game.title}, platform=${game.platformId}, emulatorId=${profile.id}, emulatorName=${profile.name}, package=${profile.packageName}, intentType=${profile.intentType}, core=${profile.corePathFor(game.platformId).orEmpty()}, rom=${game.romPath.orEmpty()}, summary=${intent.toUri(Intent.URI_INTENT_SCHEME)}"
            )
            intent
        }.onFailure { e ->
            Timber.e(
                e,
                "Failed to build launch intent: gameId=${game.id}, title=${game.title}, platform=${game.platformId}, emulatorId=${profile.id}, emulatorName=${profile.name}, rom=${game.romPath.orEmpty()}"
            )
        }
    }

    fun resolveNativeApp(game: Game): Result<Intent> {
        return runCatching {
            val packageName = game.packageName
                ?: error("No Android package recorded for ${game.title}")
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: error("Android app not installed or not launchable: $packageName")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            Timber.d(
                "Native game intent resolved: gameId=${game.id}, title=${game.title}, package=$packageName, summary=${intent.toUri(Intent.URI_INTENT_SCHEME)}"
            )
            intent
        }.onFailure { e ->
            Timber.e(
                e,
                "Failed to build native game launch intent: gameId=${game.id}, title=${game.title}, package=${game.packageName.orEmpty()}"
            )
        }
    }

    fun validateBeforeLaunch(game: Game, profile: EmulatorProfile) {
        if (profile.intentType != IntentType.CUSTOM_COMMAND) {
            try {
                context.packageManager.getPackageInfo(profile.packageName, 0)
            } catch (_: PackageManager.NameNotFoundException) {
                error("Emulator not installed: ${profile.name} (${profile.packageName})")
            }
        }

        if (profile.intentType == IntentType.COMPONENT) {
            val activityClass = profile.activityClass ?: error("Activity class required for COMPONENT intent - profile: ${profile.name}")
            try {
                context.packageManager.getActivityInfo(
                    ComponentName(profile.packageName, activityClass),
                    0,
                )
            } catch (_: PackageManager.NameNotFoundException) {
                error(
                    "${profile.name} no longer provides its launch activity " +
                        "($activityClass). Update the emulator, or pick another " +
                        "emulator for this platform."
                )
            }
        }

        if (launchesByToken(profile)) {
            if (game.launchToken.isNullOrBlank()) {
                error("No launch ID recorded for ${game.title}. Re-scan its library.")
            }
        } else if (launchesByRawPath(profile)) {
            val romPath = game.romPath
                ?: error(
                    "${profile.name} launches games by file path and PSPLauncher has no path " +
                        "for ${game.title}. Re-scan its library, or pick another emulator."
                )
            val file = File(romPath)
            if (!file.exists()) error("ROM file not found: $romPath")

            if (!file.canRead()) {
                val allFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    runCatching { Environment.isExternalStorageManager() }.getOrDefault(false)
                if (allFilesAccess) {
                    error(
                        "PSPLauncher can see ${game.title} but cannot read it at $romPath. " +
                            "${profile.name} needs direct file access to this folder."
                    )
                }
                Timber.i(
                    "Not readable by PSPLauncher, launching anyway: $romPath — no all-files " +
                        "access here, so this says nothing about ${profile.name}"
                )
            }
        } else if (!game.romUri.isNullOrBlank()) {
            val romUri = runCatching { Uri.parse(game.romUri) }.getOrNull()
                ?: error("This game's ROM link is invalid. Re-scan its library.")
            try {
                context.contentResolver.openFileDescriptor(romUri, "r")?.close()
            } catch (_: SecurityException) {
                throw LaunchBlockedException(
                    "PSPLauncher lost access to this game's file. Reconnect the " +
                        "storage and rescan its library.",
                    LaunchFailureKind.STORAGE_ACCESS_LOST,
                )
            } catch (_: Exception) {
            }
        } else {
            val romPath = game.romPath ?: error("ROM path is required to launch ${game.title}")
            if (!File(romPath).exists()) error("ROM file not found: $romPath")
        }

        if (profile.intentType == IntentType.COMPONENT && profile.coreMap.isNotEmpty()) {
            val corePath = profile.corePathFor(game.platformId)
            if (corePath.isNullOrBlank()) {
                error("No RetroArch core configured for platform '${game.platformId}' in profile '${profile.name}'. Open RetroArch → Core Downloader to install a core for this system.")
            }
        }
    }

    private fun launchesByRawPath(profile: EmulatorProfile): Boolean =
        profile.intentArrayExtras.values.flatten().any { it.contains(LaunchTemplate.ROM_PATH) } ||
            profile.intentExtras.values.any { it.contains(LaunchTemplate.ROM_PATH) }

    private fun launchesByToken(profile: EmulatorProfile): Boolean =
        profile.intentArrayExtras.values.flatten().any { it.contains(LaunchTemplate.TITLE_ID) } ||
            profile.intentExtras.values.any { it.contains(LaunchTemplate.TITLE_ID) }

    @Suppress("QueryPermissionsNeeded")
    private suspend fun buildViewIntent(game: Game, profile: EmulatorProfile): Intent {
        val uri = romLaunchUri(game, profile)
        val activityClass = profile.activityClass
        val mime = profile.mimeType ?: "application/octet-stream"

        fun build(withType: Boolean, withComponent: Boolean): Intent =
            Intent(Intent.ACTION_VIEW).apply {
                if (withType) setDataAndType(uri, mime) else data = uri
                if (withComponent && activityClass != null) {
                    component = ComponentName(profile.packageName, activityClass)
                } else {
                    setPackage(profile.packageName)
                }
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                applyProfileFlags(profile)
            }

        val candidates = listOf(
            build(withType = true,  withComponent = true),
            build(withType = false, withComponent = true),
            build(withType = true,  withComponent = false),
            build(withType = false, withComponent = false),
        )
        val intent = candidates.firstOrNull {
            context.packageManager.queryIntentActivities(it, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
        } ?: run {
            Timber.w(
                "Intent not resolvable: profile=${profile.name}, package=${profile.packageName}, uri=${uri.scheme}://, mime=$mime, activity=${activityClass ?: "(by package)"}"
            )
            error("${profile.name} cannot open this ROM type. Try reinstalling the emulator, or verify its app permissions in Android Settings.")
        }

        intent.grantReadPermissionIfNeeded(uri, game.title, profile.packageName)
        Timber.d("View intent resolved: package=${profile.packageName}, type=${intent.type ?: "(none)"}, component=${intent.component?.shortClassName ?: "(by package)"}")
        return intent
    }

    private suspend fun buildComponentIntent(game: Game, profile: EmulatorProfile): Intent {
        val activity = profile.activityClass
            ?: error("Activity class required for COMPONENT intent - profile: ${profile.name}")

        val needsRomUri = profile.attachRomData ||
            profile.intentExtras.values.any { it.contains(LaunchTemplate.ROM_URI) }
        val romUri: Uri? = if (needsRomUri) {
            game.romUri?.takeIf { it.isNotBlank() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
                ?: romUriMinter.mint(game.romPath ?: error("ROM path required for ${profile.name}"))
        } else null

        val action = profile.intentAction ?: Intent.ACTION_MAIN
        return Intent(action).apply {
            component = ComponentName(profile.packageName, activity)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            applyProfileFlags(profile)

            if (profile.attachRomData && romUri != null) data = romUri

            profile.intentCategory?.let { addCategory(it) }

            profile.intentExtras.forEach { (key, valueTemplate) ->
                putExtra(key, resolveTemplate(valueTemplate, game, profile, romUri))
            }
            profile.intentBoolExtras.forEach { (key, value) ->
                putExtra(key, value)
            }

            profile.intentArrayExtras.forEach { (key, templates) ->
                putExtra(key, templates.map { resolveTemplate(it, game, profile, romUri) }.toTypedArray())
            }

            if (romUri != null) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, game.title, romUri)
                context.grantUriPermission(profile.packageName, romUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun buildCustomCommandIntent(game: Game, profile: EmulatorProfile): Intent {
        val command = profile.customCommand
            ?: error("Custom command required for CUSTOM_COMMAND intent")
        val resolved = resolveTemplate(command, game, profile)
        Timber.d("Custom launch command resolved: $resolved")
        return parseAmCommand(resolved, profile.packageName)
    }

    private suspend fun romLaunchUri(game: Game, profile: EmulatorProfile): Uri {
        game.romUri?.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }

        val romPath = game.romPath ?: error("ROM path is required to launch ${game.title}")
        if (profile.useFileUri && !profile.useSafUri && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return Uri.fromFile(File(romPath))
        }
        if (profile.useFileUri && !profile.useSafUri) {
            Timber.d(
                "Profile ${profile.id} requests file:// ROM launch; using granted content:// URI on API ${Build.VERSION.SDK_INT}"
            )
        }

        return romUriMinter.mint(romPath)
            ?: error(
                "${game.title} is not inside a configured ROM folder, so it cannot be handed to " +
                    "${profile.name}. Check the folder set for this Memory Card in Settings."
            )
    }

    private fun Intent.applyProfileFlags(profile: EmulatorProfile) {
        profile.intentFlags.forEach { flag ->
            when (flag) {
                "CLEAR_TASK" -> addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                "CLEAR_TOP"  -> addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }
    }

    private fun Intent.grantReadPermissionIfNeeded(uri: Uri, title: String, packageName: String) {
        if (uri.scheme != "content") return
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newUri(context.contentResolver, title, uri)
        context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun resolveTemplate(
        template: String,
        game: Game,
        profile: EmulatorProfile,
        romUri: Uri? = null,
    ): String {
        val romFile = game.romPath?.let { File(it) }
        val corePath = profile.corePathFor(game.platformId) ?: ""

        return template
            .replace(LaunchTemplate.ROM_PATH, game.romPath ?: "")
            .replace(LaunchTemplate.ROM_URI, romUri?.toString() ?: "")
            .replace(LaunchTemplate.ROM_NAME, romFile?.nameWithoutExtension ?: "")
            .replace(LaunchTemplate.ROM_DIR, romFile?.parent ?: "")
            .replace(LaunchTemplate.CORE_PATH, corePath)
            .replace(LaunchTemplate.CONFIG_PATH, retroarchConfigPath(profile.packageName))
            .replace(LaunchTemplate.PACKAGE, profile.packageName)
            .replace(LaunchTemplate.PLATFORM, game.platformId)
            .replace(LaunchTemplate.TITLE_ID, game.launchToken ?: "")
    }

    private fun retroarchConfigPath(packageName: String): String =
        "/storage/emulated/0/Android/data/$packageName/files/retroarch.cfg"

    private fun parseAmCommand(command: String, packageName: String): Intent {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setPackage(packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val tokens = command.trim().split("\\s+".toRegex())
        var i = 0
        while (i < tokens.size) {
            when (tokens[i]) {
                "-e", "--es" -> {
                    if (i + 2 < tokens.size) {
                        intent.putExtra(tokens[i + 1], tokens[i + 2])
                        i += 3
                    } else i++
                }
                "-n" -> {
                    if (i + 1 < tokens.size) {
                        val cn = tokens[i + 1].split("/")
                        if (cn.size == 2) intent.component = ComponentName(cn[0], cn[1])
                        i += 2
                    } else i++
                }
                else -> i++
            }
        }
        Timber.d("Parsed am command: package=$packageName, extras=${intent.extras?.keySet()?.joinToString()}, component=${intent.component}")
        return intent
    }
}
