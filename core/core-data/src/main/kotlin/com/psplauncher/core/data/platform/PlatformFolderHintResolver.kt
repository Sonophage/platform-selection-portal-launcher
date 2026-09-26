package com.psplauncher.core.data.platform

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlatformFolderHintResolver @Inject constructor() {
    private val hints: Map<String, String> = mapOf(

        "psx"                       to "psx",
        "ps1"                       to "psx",
        "playstation"               to "psx",
        "playstation1"              to "psx",
        "playstation 1"             to "psx",
        "sony playstation"          to "psx",
        "pcsxr"                     to "psx",

        "ps2"                       to "ps2",
        "playstation2"              to "ps2",
        "playstation 2"             to "ps2",
        "pcsx2"                     to "ps2",
        "aethersx2"                 to "ps2",

        "ps3"                       to "ps3",
        "playstation3"              to "ps3",
        "playstation 3"             to "ps3",
        "rpcs3"                     to "ps3",

        "psp"                       to "psp",
        "playstation portable"      to "psp",
        "ppsspp"                    to "psp",
        "ppsspp game"               to "psp",
        "game"                      to "psp",

        "psvita"                    to "psvita",
        "vita"                      to "psvita",
        "ps vita"                   to "psvita",
        "playstation vita"          to "psvita",

        "nes"                       to "nes",
        "famicom"                   to "nes",
        "nintendo entertainment system" to "nes",

        "snes"                      to "snes",
        "super nintendo"            to "snes",
        "super famicom"             to "snes",
        "superfamicom"              to "snes",
        "sfc"                       to "snes",

        "n64"                       to "n64",
        "nintendo 64"               to "n64",
        "nintendo64"                to "n64",

        "gb"                        to "gb",
        "gameboy"                   to "gb",
        "game boy"                  to "gb",
        "game boy original"         to "gb",
        "dmg"                       to "gb",

        "gbc"                       to "gbc",
        "gameboy color"             to "gbc",
        "game boy color"            to "gbc",

        "gba"                       to "gba",
        "gameboy advance"           to "gba",
        "game boy advance"          to "gba",

        "nds"                       to "nds",
        "ds"                        to "nds",
        "nintendo ds"               to "nds",
        "desmume"                   to "nds",
        "drastic"                   to "nds",

        "n3ds"                      to "n3ds",
        "3ds"                       to "n3ds",
        "nintendo 3ds"              to "n3ds",
        "citra"                     to "n3ds",

        "gc"                        to "gc",
        "gamecube"                  to "gc",
        "nintendo gamecube"         to "gc",
        "dolphin"                   to "gc",

        "wii"                       to "wii",
        "nintendo wii"              to "wii",

        "wiiu"                      to "wiiu",
        "wii u"                     to "wiiu",
        "nintendo wii u"            to "wiiu",
        "cemu"                      to "wiiu",

        "switch"                    to "switch",
        "nintendo switch"           to "switch",
        "ryujinx"                   to "switch",
        "yuzu"                      to "switch",

        "virtualboy"                to "virtualboy",
        "virtual boy"               to "virtualboy",

        "megadrive"                 to "megadrive",
        "genesis"                   to "megadrive",
        "md"                        to "megadrive",
        "mega drive"                to "megadrive",
        "sega genesis"              to "megadrive",
        "sega mega drive"           to "megadrive",
        "sega megadrive"            to "megadrive",

        "saturn"                    to "saturn",
        "sega saturn"               to "saturn",
        "yabasanshiro"              to "saturn",

        "dreamcast"                 to "dreamcast",
        "dc"                        to "dreamcast",
        "sega dreamcast"            to "dreamcast",
        "flycast"                   to "dreamcast",
        "redream"                   to "dreamcast",

        "gamegear"                  to "gamegear",
        "game gear"                 to "gamegear",
        "gg"                        to "gamegear",
        "sega game gear"            to "gamegear",

        "mastersystem"              to "mastersystem",
        "master system"             to "mastersystem",
        "sms"                       to "mastersystem",
        "sega master system"        to "mastersystem",
        "mark iii"                  to "mastersystem",

        "segacd"                    to "segacd",
        "sega cd"                   to "segacd",
        "mega cd"                   to "segacd",
        "megacd"                    to "segacd",

        "sega32x"                   to "sega32x",
        "32x"                       to "sega32x",
        "sega 32x"                  to "sega32x",

        "atari2600"                 to "atari2600",
        "atari 2600"                to "atari2600",
        "atari"                     to "atari2600",

        "atari5200"                 to "atari5200",
        "atari 5200"                to "atari5200",

        "atari7800"                 to "atari7800",
        "atari 7800"                to "atari7800",

        "atarilynx"                 to "atarilynx",
        "atari lynx"                to "atarilynx",
        "lynx"                      to "atarilynx",

        "pcengine"                  to "pcengine",
        "turbografx"                to "pcengine",
        "turbografx-16"             to "pcengine",
        "turbografx16"              to "pcengine",
        "pc engine"                 to "pcengine",
        "pce"                       to "pcengine",
        "tg16"                      to "pcengine",
        "tgcd"                      to "pcengine",
        "pcenginecd"                to "pcengine",

        "neogeo"                    to "neogeo",
        "neo geo"                   to "neogeo",
        "neo-geo"                   to "neogeo",

        "ngp"                       to "ngp",
        "ngpc"                      to "ngp",
        "neo geo pocket"            to "ngp",
        "neogeopocket"              to "ngp",

        "wonderswan"                to "wonderswan",
        "wonderswancolor"           to "wonderswancolor",
        "wonder swan"               to "wonderswan",
        "wonder swan color"         to "wonderswancolor",
        "wsc"                       to "wonderswancolor",

        "c64"                       to "c64",
        "commodore 64"              to "c64",
        "commodore64"               to "c64",
        "vice"                      to "c64",

        "mame"                      to "mame",
        "arcade"                    to "mame",
        "fbneo"                     to "mame",
        "finalburn"                 to "mame",
        "final burn"                to "mame",
        "fba"                       to "mame",
        "naomi"                     to "mame",

        "cps1"                      to "cps1",
        "cps-1"                     to "cps1",
        "cps 1"                     to "cps1",
        "capcom play system"        to "cps1",
        "cps2"                      to "cps2",
        "cps-2"                     to "cps2",
        "cps 2"                     to "cps2",
        "cps3"                      to "cps3",
        "cps-3"                     to "cps3",
        "cps 3"                     to "cps3",

        "xbox"                      to "xbox",
        "microsoft xbox"            to "xbox",
        "xemu"                      to "xbox",
        "xbox360"                   to "x360",
        "x360"                      to "x360",
        "xbox 360"                  to "x360",

        "windows"                   to "windows",
        "winlator"                  to "windows",
        "pc"                        to "windows",

        "dos"                       to "dos",
        "dosbox"                    to "dos",
        "ports"                     to "ports",
        "scummvm"                   to "scummvm",
    )

    fun detectFromPath(filePath: String): String? {
        val segments = filePath
            .replace('\\', '/')
            .split('/')
            .dropLast(1)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .reversed()

        for (segment in segments) {
            hints[segment]?.let { return it }
        }
        return null
    }

    fun detectFromFolderName(name: String): String? =
        hints[name.trim().lowercase()]

    fun esDeFolderName(platformId: String): String = when (platformId) {
        "x360" -> "xbox360"
        else   -> platformId
    }
}
