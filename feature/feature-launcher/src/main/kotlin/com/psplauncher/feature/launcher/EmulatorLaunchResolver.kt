package com.psplauncher.feature.launcher

import com.psplauncher.core.domain.model.EmulatorProfile
import com.psplauncher.core.domain.model.IntentType

enum class LaunchSource(

    val label: String,

    val configuredErrorPhrase: String?,
) {
    PER_GAME_OVERRIDE("Per-game override", "per-game override"),

    MEMORY_CARD("Memory card default", "memory card emulator"),

    PLATFORM_DEFAULT("Platform default", "platform default"),

    CATALOG_DEFAULT("Recommended", null),
}

data class ResolvedLaunch(
    val profile: EmulatorProfile,
    val source: LaunchSource,

    val corePath: String? = null,
) {
    val coreName: String?
        get() = corePath?.let { RetroArchCoreScanner.labelForPath(it) }

    val isMissingCore: Boolean
        get() = corePath == null &&
            profile.intentType == IntentType.COMPONENT &&
            profile.coreMap.isNotEmpty()
}

object EmulatorLaunchResolver {
    fun resolve(
        platformId: String,
        installedProfiles: List<EmulatorProfile>,
        platformProfiles: List<EmulatorProfile>,
        perGameOverride: String? = null,
        memoryCardEmulatorId: String? = null,
        platformDefault: String? = null,
    ): Result<ResolvedLaunch> {
        fun unresolvableMessage(configuredIdOrPackage: String, source: LaunchSource): String {
            val shelved = installedProfiles.firstOrNull {
                it.id == configuredIdOrPackage || it.packageName == configuredIdOrPackage
            }
            return when {
                shelved == null ->
                    "The ${source.configuredErrorPhrase} emulator is not installed or " +
                        "available: $configuredIdOrPackage"
                shelved.isRetroArchProfile() ->
                    "${shelved.name} is set as the ${source.label.lowercase()}, but that core is " +
                        "not installed in RetroArch. Install it, or choose another emulator."
                else ->
                    "${shelved.name} is set as the ${source.label.lowercase()}, but is no longer " +
                        "available. Choose another emulator."
            }
        }

        fun resolveConfigured(
            configuredIdOrPackage: String,
            source: LaunchSource,
        ): Result<ResolvedLaunch> {
            val available = installedProfiles.filter { it.isAvailable }
            val profile = available.firstOrNull { it.id == configuredIdOrPackage }
                ?: available.firstOrNull {
                    it.packageName == configuredIdOrPackage && it.supportsPlatform(platformId)
                }
                ?: available.firstOrNull { it.packageName == configuredIdOrPackage }
                ?: return Result.failure(
                    IllegalStateException(unresolvableMessage(configuredIdOrPackage, source))
                )

            if (!profile.supportsPlatform(platformId)) {
                return Result.failure(
                    IllegalStateException(
                        "${profile.name} is not configured for ${platformId.uppercase()}"
                    )
                )
            }

            return Result.success(
                ResolvedLaunch(profile = profile, source = source, corePath = profile.corePathFor(platformId))
            )
        }

        perGameOverride?.let { return resolveConfigured(it, LaunchSource.PER_GAME_OVERRIDE) }
        memoryCardEmulatorId?.let { return resolveConfigured(it, LaunchSource.MEMORY_CARD) }
        platformDefault?.let { return resolveConfigured(it, LaunchSource.PLATFORM_DEFAULT) }

        platformProfiles.firstOrNull()?.let {
            return Result.success(
                ResolvedLaunch(profile = it, source = LaunchSource.CATALOG_DEFAULT, corePath = it.corePathFor(platformId))
            )
        }

        return Result.failure(
            IllegalStateException(
                "No emulator configured for ${platformId.uppercase()}. Choose an emulator for " +
                    "this game or set a platform default."
            )
        )
    }
}
