package com.psplauncher.core.domain.model

import com.psplauncher.themekit.ColorCascade

enum class XmbColorScheme {
    ORIGINAL,
    CLASSIC_BLUE,
    SUNSET_ORANGE,
    FRESH_GREEN,
    ROYAL_PURPLE,
    CRIMSON_RED,
    SILVER_MONO,
    SAKURA_PINK,
    GOLDEN_AMBER,
    AQUA_TEAL,
    MIDNIGHT_NAVY,
    CHARCOAL,
    BLACK,
}

data class XmbPalette(
    val waveColor: Long,
    val accentColor: Long,
    val textColor: Long,
    val backgroundTop: Long,
    val backgroundBottom: Long,
)

fun XmbColorScheme.displayLabel(): String = when (this) {
    XmbColorScheme.ORIGINAL      -> "Original (Monthly)"
    XmbColorScheme.CLASSIC_BLUE  -> "Classic Blue"
    XmbColorScheme.SUNSET_ORANGE -> "Sunset Orange"
    XmbColorScheme.FRESH_GREEN   -> "Fresh Green"
    XmbColorScheme.ROYAL_PURPLE  -> "Royal Purple"
    XmbColorScheme.CRIMSON_RED   -> "Crimson Red"
    XmbColorScheme.SILVER_MONO   -> "Silver"
    XmbColorScheme.SAKURA_PINK   -> "Sakura Pink"
    XmbColorScheme.GOLDEN_AMBER  -> "Golden Amber"
    XmbColorScheme.AQUA_TEAL     -> "Aqua Teal"
    XmbColorScheme.MIDNIGHT_NAVY -> "Midnight Navy"
    XmbColorScheme.CHARCOAL      -> "Charcoal"
    XmbColorScheme.BLACK         -> "Black"
}

fun XmbColorScheme.resolve(month: Int): XmbPalette {
    val wave = when (this) {
        XmbColorScheme.ORIGINAL      -> ORIGINAL_MONTH_WAVE[month.coerceIn(1, 12) - 1]
        XmbColorScheme.CLASSIC_BLUE  -> 0xFF0055AAL
        XmbColorScheme.SUNSET_ORANGE -> 0xFFFF8A3DL
        XmbColorScheme.FRESH_GREEN   -> 0xFF36C26BL
        XmbColorScheme.ROYAL_PURPLE  -> 0xFF7A4DD6L
        XmbColorScheme.CRIMSON_RED   -> 0xFFE03B4FL
        XmbColorScheme.SILVER_MONO   -> 0xFFB8C4D0L
        XmbColorScheme.SAKURA_PINK   -> 0xFFE87FB0L
        XmbColorScheme.GOLDEN_AMBER  -> 0xFFE0A32EL
        XmbColorScheme.AQUA_TEAL     -> 0xFF2EC4B6L
        XmbColorScheme.MIDNIGHT_NAVY -> 0xFF23477EL
        XmbColorScheme.CHARCOAL      -> 0xFF4A505AL

        XmbColorScheme.BLACK         -> 0xFF000000L
    }
    return XmbPalette(
        waveColor        = wave,
        accentColor      = 0xFFFFFFFFL,
        textColor        = 0xFFFFFFFFL,
        backgroundTop    = lightBackgroundAnchors(wave).first,
        backgroundBottom = lightBackgroundAnchors(wave).second,
    )
}

fun lightBackgroundAnchors(waveArgb: Long): Pair<Long, Long> =
    ColorCascade.lightBackgroundAnchors(waveArgb)

private val ORIGINAL_MONTH_WAVE = longArrayOf(
    0xFF1FA89CL,
    0xFFE56BA0L,
    0xFF6FBF3BL,
    0xFFE99BC4L,
    0xFF34B3A0L,
    0xFF3A7BD5L,
    0xFF35B6D6L,
    0xFF2E54A8L,
    0xFFE08A2EL,
    0xFF8A5AC2L,
    0xFFB5642EL,
    0xFFD23B4EL,
)
