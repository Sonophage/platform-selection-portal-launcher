package com.psplauncher.feature.artwork.store

data class ArtworkCanvas(
    val width: Int,
    val height: Int,
    val sourceAspectPreferred: Boolean = false,
) {
    val aspectRatio: Float get() = width.toFloat() / height.toFloat()
}

object ArtworkDimensions {
    val GenericBoxArt = ArtworkCanvas(430, 600)

    fun boxArt(platformId: String?): ArtworkCanvas =
        BOX_ART_CANVAS[canonicalize(platformId)] ?: GenericBoxArt

    fun hasBoxArtPreset(platformId: String?): Boolean =
        BOX_ART_CANVAS.containsKey(canonicalize(platformId))

    fun boxArtAspect(platformId: String?, sourceWidth: Int?, sourceHeight: Int?): Float {
        if (sourceWidth != null && sourceHeight != null && sourceWidth > 0 && sourceHeight > 0) {
            return sourceWidth.toFloat() / sourceHeight.toFloat()
        }
        return boxArt(platformId).aspectRatio
    }

    private val ALIASES = mapOf(
        "ps1" to "psx",
        "sfc" to "snes",
        "dc" to "dreamcast",
        "nx" to "switch",
        "ds" to "nds",
        "3ds" to "n3ds",
        "ngpc" to "ngp",
    )

    private fun canonicalize(platformId: String?): String? {
        val id = platformId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return ALIASES[id] ?: id
    }

    private val BOX_ART_CANVAS = mapOf(

        "psx" to ArtworkCanvas(600, 600),
        "ps2" to ArtworkCanvas(430, 600),
        "ps3" to ArtworkCanvas(480, 600),
        "psp" to ArtworkCanvas(354, 600),
        "psvita" to ArtworkCanvas(468, 600),

        "nes" to ArtworkCanvas(430, 600),
        "snes" to ArtworkCanvas(600, 438, sourceAspectPreferred = true),
        "n64" to ArtworkCanvas(600, 438, sourceAspectPreferred = true),
        "gb" to ArtworkCanvas(600, 600),
        "gbc" to ArtworkCanvas(600, 600),
        "gba" to ArtworkCanvas(600, 600),
        "nds" to ArtworkCanvas(540, 600),
        "n3ds" to ArtworkCanvas(540, 600),
        "gc" to ArtworkCanvas(430, 600),
        "wii" to ArtworkCanvas(430, 600),
        "wiiu" to ArtworkCanvas(430, 600),
        "switch" to ArtworkCanvas(366, 600),
        "virtualboy" to ArtworkCanvas(600, 600),

        "megadrive" to ArtworkCanvas(430, 600),
        "mastersystem" to ArtworkCanvas(430, 600),
        "gamegear" to ArtworkCanvas(424, 600),
        "saturn" to ArtworkCanvas(600, 600, sourceAspectPreferred = true),
        "dreamcast" to ArtworkCanvas(600, 600),
        "segacd" to ArtworkCanvas(420, 600, sourceAspectPreferred = true),
        "sega32x" to ArtworkCanvas(430, 600),

        "atari2600" to ArtworkCanvas(440, 600),
        "atari5200" to ArtworkCanvas(440, 600),
        "atari7800" to ArtworkCanvas(440, 600),
        "atarilynx" to ArtworkCanvas(488, 600, sourceAspectPreferred = true),

        "pcengine" to ArtworkCanvas(600, 600, sourceAspectPreferred = true),

        "neogeo" to ArtworkCanvas(480, 600),
        "ngp" to ArtworkCanvas(600, 600),

        "wonderswan" to ArtworkCanvas(600, 600),
        "wonderswancolor" to ArtworkCanvas(600, 600),

        "c64" to ArtworkCanvas(430, 600, sourceAspectPreferred = true),

        "mame" to ArtworkCanvas(600, 600, sourceAspectPreferred = true),
        "cps1" to ArtworkCanvas(600, 600, sourceAspectPreferred = true),
        "cps2" to ArtworkCanvas(600, 600, sourceAspectPreferred = true),
        "cps3" to ArtworkCanvas(600, 600, sourceAspectPreferred = true),

        "xbox" to ArtworkCanvas(430, 600),
        "x360" to ArtworkCanvas(430, 600),

        "windows" to ArtworkCanvas(600, 600, sourceAspectPreferred = true),
        "android" to ArtworkCanvas(600, 600, sourceAspectPreferred = true),
    )
}
