package com.psplauncher.feature.launcher

import timber.log.Timber

data class RetroArchCore(
    val name: String,
    val fileName: String,
    val absolutePath: String,
    val platformIds: List<String>,
)

object RetroArchCoreScanner {
    val RETROARCH_PACKAGES = listOf(
        "com.retroarch.aarch64",
        "com.retroarch.ra64",
        "com.retroarch",
        "com.retroarch.ra32",
    )

    private data class RecommendedCore(
        val fileName: String,
        val name: String,
        val platformIds: List<String>,
    )

    private val RECOMMENDED_CORES = listOf(
        RecommendedCore("mesen_libretro_android.so",            "Mesen (NES)",              listOf("nes", "fam")),
        RecommendedCore("snes9x_libretro_android.so",           "Snes9x (SNES)",            listOf("snes")),
        RecommendedCore("mupen64plus_next_libretro_android.so", "Mupen64Plus-Next (N64)",   listOf("n64")),
        RecommendedCore("gambatte_libretro_android.so",         "Gambatte (GB/GBC)",        listOf("gb", "gbc")),
        RecommendedCore("mgba_libretro_android.so",             "mGBA (GBA)",               listOf("gba", "gb", "gbc")),
        RecommendedCore("genesis_plus_gx_libretro_android.so",  "Genesis Plus GX",          listOf("megadrive", "genesis", "mastersystem", "sms", "gamegear", "segacd")),
        RecommendedCore("picodrive_libretro_android.so",        "PicoDrive (32X)",          listOf("sega32x", "megadrive", "genesis")),
        RecommendedCore("mednafen_saturn_libretro_android.so",  "Beetle Saturn",            listOf("saturn")),
        RecommendedCore("mednafen_psx_hw_libretro_android.so",  "Beetle PSX HW",            listOf("psx", "ps1")),
        RecommendedCore("mednafen_pce_libretro_android.so",     "Beetle PCE",               listOf("pcengine", "pce", "tgfx16")),
        RecommendedCore("fbneo_libretro_android.so",            "FinalBurn Neo (Arcade)",   listOf("arcade", "neogeo", "mame", "cps1", "cps2", "cps3")),
        RecommendedCore("mednafen_ngp_libretro_android.so",     "Beetle NeoPop (NGP)",      listOf("ngp", "ngpc")),
        RecommendedCore("mednafen_wswan_libretro_android.so",   "Beetle Cygne (WonderSwan)", listOf("wonderswan", "wonderswancolor", "ws", "wsc")),
        RecommendedCore("stella_libretro_android.so",           "Stella (Atari 2600)",      listOf("atari2600")),
        RecommendedCore("a5200_libretro_android.so",            "a5200 (Atari 5200)",       listOf("atari5200")),
        RecommendedCore("prosystem_libretro_android.so",        "ProSystem (Atari 7800)",   listOf("atari7800")),
        RecommendedCore("handy_libretro_android.so",            "Handy (Lynx)",             listOf("atarilynx", "lynx")),
        RecommendedCore("mednafen_vb_libretro_android.so",      "Beetle VB (Virtual Boy)",  listOf("virtualboy", "vb")),
        RecommendedCore("vice_x64_libretro_android.so",         "VICE x64 (C64)",           listOf("c64")),
    )

    private val CORE_PLATFORM_MAP: Map<String, List<String>> = mapOf(
        "nestopia"                    to listOf("nes", "fam"),
        "mesen"                       to listOf("nes", "fam"),
        "fceumm"                      to listOf("nes", "fam"),
        "snes9x"                      to listOf("snes"),
        "bsnes"                       to listOf("snes"),
        "bsnes_mercury_accuracy"      to listOf("snes"),
        "bsnes_mercury_balanced"      to listOf("snes"),
        "mesen-s"                     to listOf("snes"),
        "genesis_plus_gx"             to listOf("genesis", "megadrive", "mastersystem", "sms", "gamegear", "segacd"),
        "genesis_plus_gx_wide"        to listOf("genesis", "megadrive"),
        "picodrive"                   to listOf("genesis", "megadrive", "sms", "gamegear", "sega32x"),
        "gambatte"                    to listOf("gb", "gbc"),
        "mgba"                        to listOf("gb", "gbc", "gba"),
        "vba_next"                    to listOf("gba"),
        "vbam"                        to listOf("gba", "gb", "gbc"),
        "mupen64plus_next"            to listOf("n64"),
        "mupen64plus_next_gles3"      to listOf("n64"),
        "mupen64plus_next_gles2"      to listOf("n64"),
        "parallel_n64"                to listOf("n64"),
        "swanstation"                 to listOf("psx", "ps1"),
        "pcsx_rearmed"                to listOf("psx", "ps1"),
        "mednafen_psx"                to listOf("psx", "ps1"),
        "mednafen_psx_hw"             to listOf("psx", "ps1"),
        "pcsx2"                       to listOf("ps2"),
        "ppsspp"                      to listOf("psp"),
        "kronos"                      to listOf("saturn"),
        "desmume"                     to listOf("nds", "ds"),
        "melonds"                     to listOf("nds", "ds"),
        "dolphin"                     to listOf("gc", "gamecube", "wii"),
        "citra"                       to listOf("3ds", "n3ds"),
        "mame2003_plus"               to listOf("arcade", "cps1", "cps2"),
        "mame2003"                    to listOf("arcade", "cps1", "cps2"),
        "mame"                        to listOf("arcade", "cps1", "cps2", "cps3"),
        "fbneo"                       to listOf("arcade", "neogeo", "cps1", "cps2", "cps3"),
        "mednafen_pce"                to listOf("pce", "pcengine", "tgfx16"),
        "mednafen_pce_fast"           to listOf("pce", "pcengine", "tgfx16"),
        "mednafen_saturn"             to listOf("saturn"),
        "yabause"                     to listOf("saturn"),
        "yabasanshiro"                to listOf("saturn"),
        "stella"                      to listOf("atari2600"),
        "a5200"                       to listOf("atari5200"),
        "prosystem"                   to listOf("atari7800"),
        "smsplus"                     to listOf("sms", "mastersystem", "gamegear"),
        "bluemsx"                     to listOf("msx"),
        "fmsx"                        to listOf("msx"),
        "puae"                        to listOf("amiga"),
        "vice_x64"                    to listOf("c64"),
        "vice_x128"                   to listOf("c64"),
        "mednafen_lynx"               to listOf("lynx", "atarilynx"),
        "handy"                       to listOf("lynx", "atarilynx"),
        "mednafen_vb"                 to listOf("vb", "virtualboy"),
        "mednafen_wswan"              to listOf("ws", "wsc", "wonderswan", "wonderswancolor"),
        "race"                        to listOf("ngp", "ngpc"),
        "mednafen_ngp"                to listOf("ngp", "ngpc"),
        "opera"                       to listOf("3do"),
        "flycast"                     to listOf("dreamcast", "dc", "naomi", "atomiswave"),
    )

    fun labelForPath(corePath: String): String {
        val fileName = corePath.substringAfterLast('/')
        if (fileName.isBlank()) return corePath
        RECOMMENDED_CORES.firstOrNull { it.fileName == fileName }?.name?.let { return it }
        val prefix = fileName
            .removeSuffix("_libretro_android.so")
            .removeSuffix("_libretro.so")
        if (prefix != fileName) {
            return prefix.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
        }
        return fileName
    }

    @Suppress("SdCardPath")
    fun coresFor(packageName: String, installedCoreFiles: Set<String>): List<RetroArchCore> {
        val internalDir = "/data/data/$packageName/cores"
        val cores = installedCoreFiles.mapNotNull { fileName ->
            val prefix = fileName
                .removeSuffix("_libretro_android.so")
                .removeSuffix("_libretro.so")
            val platforms = CORE_PLATFORM_MAP[prefix] ?: return@mapNotNull null
            RetroArchCore(
                name         = RECOMMENDED_CORES.firstOrNull { it.fileName == fileName }?.name
                               ?: prefix.replace('_', ' ').replaceFirstChar { it.uppercaseChar() },
                fileName     = fileName,
                absolutePath = "$internalDir/$fileName",
                platformIds  = platforms,
            )
        }.sortedBy { it.name }
        Timber.i(
            "RetroArch cores ($packageName): ${cores.size} mapped of ${installedCoreFiles.size} installed"
        )
        return cores
    }

    fun recommendedCoreNameFor(platformId: String): String? =
        RECOMMENDED_CORES.firstOrNull { platformId in it.platformIds }?.name
}
