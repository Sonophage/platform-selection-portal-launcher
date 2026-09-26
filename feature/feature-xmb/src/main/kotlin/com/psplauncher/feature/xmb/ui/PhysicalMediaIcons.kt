package com.psplauncher.feature.xmb.ui

import androidx.annotation.DrawableRes
import com.psplauncher.feature.xmb.R

fun physicalMediaAssetName(platformId: String?): String? = when (platformId) {
    "ps1"                            -> "psx"
    "fam", "famicom"                 -> "nes"
    "ds"                             -> "nds"
    "3ds"                            -> "n3ds"
    "nx"                             -> "switch"
    "gamecube"                       -> "gc"
    "md"                             -> "megadrive"
    "sms"                            -> "mastersystem"
    "dc"                             -> "dreamcast"
    "naomi", "atomiswave"            -> "arcade"
    "pce"                            -> "pcengine"
    "tgfx16"                         -> "tg16"
    "lynx"                           -> "atarilynx"
    "vb"                             -> "virtualboy"
    "ws"                             -> "wonderswan"
    "wsc"                            -> "wonderswancolor"

    "android", "steam",
    "gog", "default"                 -> null

    else                             -> platformId
}

@DrawableRes
fun physicalMediaIconRes(platformId: String?): Int? = when (platformId) {
    "psx", "ps1",
    "ps2", "ps3",
    "saturn", "segacd", "sega32x",
    "dreamcast", "dc", "naomi", "atomiswave",
    "wii", "wiiu",
    "windows",
    "xbox", "x360", "xbox360"       -> R.drawable.media_disc

    "psp"                           -> R.drawable.media_umd
    "psvita"                        -> R.drawable.media_cartridge_vita
    "gc", "gamecube"                -> R.drawable.media_disc_mini
    "switch", "nx"                  -> R.drawable.media_cartridge_switch

    "nds", "ds",
    "n3ds", "3ds"                   -> R.drawable.media_cartridge_ds

    "gb", "gbc", "gba",
    "virtualboy", "vb"              -> R.drawable.media_cartridge_gb

    "nes", "fam", "famicom",
    "snes", "sfc", "n64",
    "genesis", "megadrive", "md",
    "mastersystem", "sms",
    "atari2600", "atari5200", "atari7800",
    "neogeo", "3do"                 -> R.drawable.media_cartridge

    "gamegear", "ngp", "ngpc",
    "atarilynx", "lynx",
    "wonderswan", "ws",
    "wonderswancolor", "wsc"        -> R.drawable.media_cartridge_gb

    "pcengine", "pce", "tgfx16"    -> R.drawable.media_hucard
    "c64", "amiga", "msx"          -> R.drawable.media_floppy

    "android", "steam",
    "gog", "arcade", "mame",
    "default"                       -> null

    else                            -> null
}
