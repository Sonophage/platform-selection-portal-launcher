package com.psplauncher.feature.launcher

import com.psplauncher.core.domain.model.EmulatorProfile

fun platformAliases(platformId: String): List<String> = when (platformId) {
    "psx"          -> listOf("psx", "ps1")
    "ps1"          -> listOf("ps1", "psx")
    "n3ds"         -> listOf("n3ds", "3ds")
    "3ds"          -> listOf("3ds", "n3ds")
    "gc"           -> listOf("gc", "gamecube")
    "gamecube"     -> listOf("gamecube", "gc")
    "nds"          -> listOf("nds", "ds")
    "ds"           -> listOf("ds", "nds")
    "pcengine"     -> listOf("pcengine", "pce", "tgfx16")
    "pce"          -> listOf("pce", "pcengine", "tgfx16")
    "tgfx16"       -> listOf("tgfx16", "pce", "pcengine")
    "mastersystem" -> listOf("mastersystem", "sms")
    "sms"          -> listOf("sms", "mastersystem")
    "genesis"      -> listOf("genesis", "megadrive", "md")
    "megadrive"    -> listOf("megadrive", "genesis", "md")
    "md"           -> listOf("md", "genesis", "megadrive")
    "dreamcast"    -> listOf("dreamcast", "dc")
    "dc"           -> listOf("dc", "dreamcast")
    "virtualboy"   -> listOf("virtualboy", "vb")
    "vb"           -> listOf("vb", "virtualboy")
    "atarilynx"    -> listOf("atarilynx", "lynx")
    "lynx"         -> listOf("lynx", "atarilynx")
    "wonderswan"   -> listOf("wonderswan", "ws")
    "ws"           -> listOf("ws", "wonderswan")
    "wonderswancolor" -> listOf("wonderswancolor", "wsc")
    "wsc"          -> listOf("wsc", "wonderswancolor")
    "ngp"          -> listOf("ngp", "ngpc")
    "ngpc"         -> listOf("ngpc", "ngp")
    else           -> listOf(platformId)
}

fun EmulatorProfile.supportsPlatform(platformId: String): Boolean {
    val aliases = platformAliases(platformId)
    return supportedPlatformIds.any { it in aliases }
}

fun EmulatorProfile.corePathFor(platformId: String): String? {
    for (alias in platformAliases(platformId)) {
        coreMap[alias]?.let { return normalizeRetroArchCorePath(it) }
    }
    return null
}

@Suppress("SdCardPath")
fun EmulatorProfile.normalizeRetroArchCorePath(corePath: String): String {
    if (!packageName.startsWith("com.retroarch")) return corePath
    return corePath
        .replace("/data/data/com.retroarch.aarch64/cores/", "/data/data/$packageName/cores/")
        .replace("/data/data/com.retroarch.ra64/cores/", "/data/data/$packageName/cores/")
        .replace("/data/data/com.retroarch.ra32/cores/", "/data/data/$packageName/cores/")
        .replace("/data/data/com.retroarch/cores/", "/data/data/$packageName/cores/")
}
