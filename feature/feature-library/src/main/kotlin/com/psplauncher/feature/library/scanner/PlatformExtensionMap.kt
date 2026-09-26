package com.psplauncher.feature.library.scanner

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformExtensionMap @Inject constructor() {
    private val definitiveExtensions = mapOf(

        "cso"   to "psp",
        "pbp"   to "psp",

        "nes"   to "nes",
        "fds"   to "nes",
        "unf"   to "nes",
        "unif"  to "nes",
        "smc"   to "snes",
        "sfc"   to "snes",
        "swc"   to "snes",
        "n64"   to "n64",
        "z64"   to "n64",
        "v64"   to "n64",
        "gb"    to "gb",
        "dmg"   to "gb",
        "gbc"   to "gbc",
        "gba"   to "gba",
        "agb"   to "gba",
        "nds"   to "nds",
        "dsi"   to "nds",
        "3ds"   to "n3ds",
        "cia"   to "n3ds",
        "gcm"   to "gc",
        "gcz"   to "gc",
        "rvz"   to "gc",
        "wbfs"  to "wii",
        "wad"   to "wii",
        "rpx"   to "wiiu",
        "wua"   to "wiiu",
        "wud"   to "wiiu",
        "nsp"   to "switch",
        "xci"   to "switch",
        "nro"   to "switch",
        "nso"   to "switch",
        "vb"    to "virtualboy",
        "vboy"  to "virtualboy",

        "md"    to "megadrive",
        "gen"   to "megadrive",
        "smd"   to "megadrive",
        "68k"   to "megadrive",
        "32x"   to "sega32x",
        "sms"   to "mastersystem",
        "sg"    to "mastersystem",
        "gg"    to "gamegear",
        "gdi"   to "dreamcast",
        "cdi"   to "dreamcast",

        "a26"   to "atari2600",
        "a52"   to "atari5200",
        "car"   to "atari5200",
        "a78"   to "atari7800",
        "lnx"   to "atarilynx",
        "lyx"   to "atarilynx",

        "pce"   to "pcengine",
        "sgx"   to "pcengine",

        "neo"   to "neogeo",
        "ngp"   to "ngp",
        "ngc"   to "ngp",
        "npc"   to "ngp",

        "ws"    to "wonderswan",
        "wsc"   to "wonderswancolor",

        "d64"   to "c64",
        "g64"   to "c64",
        "t64"   to "c64",
        "tap"   to "c64",
        "prg"   to "c64",
        "crt"   to "c64",
        "d81"   to "c64",
    )

    val contextDependentExtensions = setOf("bin", "cue", "img", "chd", "mds", "m3u", "ccd")

    val folderSensitiveExtensions = mapOf(
        "iso" to "ps2",
        "zip" to "mame",
        "7z"  to "mame",
    )

    fun detectPlatform(extension: String): String? =
        definitiveExtensions[extension.lowercase()]

    fun folderSensitiveDefault(extension: String): String? =
        folderSensitiveExtensions[extension.lowercase()]

    fun isFolderSensitive(extension: String): Boolean =
        extension.lowercase() in folderSensitiveExtensions

    fun isDefinitive(extension: String): Boolean =
        extension.lowercase() in definitiveExtensions

    fun isContextDependent(extension: String): Boolean =
        extension.lowercase() in contextDependentExtensions

    fun isPlaylist(extension: String): Boolean = extension.equals("m3u", ignoreCase = true)

    fun isKnownExtension(extension: String): Boolean =
        isDefinitive(extension) || isContextDependent(extension) || isFolderSensitive(extension)
}
