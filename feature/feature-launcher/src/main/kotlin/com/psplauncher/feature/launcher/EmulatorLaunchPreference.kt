package com.psplauncher.feature.launcher

import com.psplauncher.core.domain.model.EmulatorProfile

fun EmulatorProfile.isRetroArchProfile(): Boolean =
    autoSource == "retroarch-core" || packageName.startsWith("com.retroarch")

fun List<EmulatorProfile>.byLaunchPreference(): List<EmulatorProfile> =
    sortedBy { if (it.isRetroArchProfile()) 1 else 0 }

fun List<EmulatorProfile>.stabilizeCore(rememberedProfileId: String?): List<EmulatorProfile> {
    val remembered = rememberedProfileId?.let { id -> firstOrNull { it.id == id } } ?: return this
    if (!remembered.isRetroArchProfile()) return this
    return buildList {
        addAll(this@stabilizeCore.filter { !it.isRetroArchProfile() })
        add(remembered)
        addAll(this@stabilizeCore.filter { it.isRetroArchProfile() && it.id != remembered.id })
    }
}
