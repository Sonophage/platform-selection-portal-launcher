package com.psplauncher.themekit

val SYSICON_PLATFORM_IDS: List<String> = listOf(
    "allgames",
    "android",
    "atari2600",
    "atari5200",
    "atari7800",
    "atarilynx",
    "c64",
    "dreamcast",
    "gamegear",
    "gb",
    "gba",
    "gbc",
    "gc",
    "mame",
    "mastersystem",
    "megadrive",
    "n3ds",
    "n64",
    "nds",
    "neogeo",
    "nes",
    "ngp",
    "pcengine",
    "ps2",
    "ps3",
    "psp",
    "psvita",
    "psx",
    "saturn",
    "sega32x",
    "segacd",
    "snes",
    "switch",
    "virtualboy",
    "wii",
    "wiiu",
    "windows",
    "wonderswan",
    "wonderswancolor",
    "x360",
)

fun consoleDisplayName(platformId: String): String = when (platformId) {
    "allgames" -> "All Games"
    "atari2600" -> "Atari 2600"
    "atari5200" -> "Atari 5200"
    "atari7800" -> "Atari 7800"
    "atarilynx" -> "Atari Lynx"
    "c64" -> "Commodore 64"
    "gamegear" -> "Game Gear"
    "gb" -> "Game Boy"
    "gba" -> "Game Boy Advance"
    "gbc" -> "Game Boy Color"
    "gc" -> "GameCube"
    "mame" -> "Arcade (MAME)"
    "mastersystem" -> "Master System"
    "megadrive" -> "Mega Drive"
    "n3ds" -> "Nintendo 3DS"
    "n64" -> "Nintendo 64"
    "nds" -> "Nintendo DS"
    "neogeo" -> "Neo Geo"
    "nes" -> "NES"
    "ngp" -> "Neo Geo Pocket"
    "pcengine" -> "PC Engine"
    "ps2" -> "PlayStation 2"
    "ps3" -> "PlayStation 3"
    "psp" -> "PSP"
    "psvita" -> "PS Vita"
    "psx" -> "PlayStation"
    "sega32x" -> "Mega Drive 32X"
    "segacd" -> "Sega CD"
    "snes" -> "SNES"
    "virtualboy" -> "Virtual Boy"
    "wonderswan" -> "WonderSwan"
    "wonderswancolor" -> "WonderSwan Color"
    "x360" -> "Xbox 360"
    else -> platformId.uppercase()
}

object CustomizableIcons {
    private const val CONSOLE_TEMPLATE_PX = 256

    val ALL: List<IconSlot> = IconSlots.ALL + SYSICON_PLATFORM_IDS.map { id ->
        IconSlot(
            key = "sysicon_$id",
            group = IconSlot.Group.CONSOLE,
            displayName = consoleDisplayName(id),
            templateSizePx = CONSOLE_TEMPLATE_PX,
        )
    }

    private val byKey: Map<String, IconSlot> = ALL.associateBy { it.key }

    fun byKey(key: String): IconSlot? = byKey[key]

    fun isValidKey(key: String): Boolean = key in byKey

    fun group(group: IconSlot.Group): List<IconSlot> = ALL.filter { it.group == group }
}
