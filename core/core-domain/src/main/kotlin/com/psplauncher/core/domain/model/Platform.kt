package com.psplauncher.core.domain.model

data class Platform(
    val id: String,
    val name: String,
    val shortName: String,
    val iconRes: String?,
    val accentColor: Long,
    val isPinnedToBar: Boolean = false,
    val barPosition: Int = -1,
    val preferredEmulatorPackage: String? = null,
    val romExtensions: List<String> = emptyList(),
)

object PlatformIds {
    const val WINDOWS = "windows"

    const val ANDROID = "android"
}
