package com.psplauncher.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

@Immutable
data class StorefrontColors(

    val accentHue: Color,

    val backgroundDeep: Color,

    val backgroundMid: Color,

    val selectionGlow: Color,

    val chromeTop: Color,

    val chromeBottom: Color,

    val chromeDivider: Color,

    val categorySelected: Color,

    val categorySelectedEdge: Color,

    val categoryInactive: Color,

    val tileNormal: Color,

    val tileSelected: Color,

    val tileSelectedEdge: Color,

    val tileSelectedInner: Color,

    val footerBackground: Color,

    val footerDivider: Color,

    val textPrimary: Color,

    val textSecondary: Color,

    val contentBackground: Color,

    val railBackground: Color,

    val searchField: Color,

    val searchBorder: Color,

    val overlayDim: Color,

    val menuPanel: Color,

    val menuRowSelected: Color,

    val destructive: Color,
)

private val DefaultStorefrontColors = StorefrontColors(
    accentHue         = Color(0xFF128BC9),
    backgroundDeep    = Color(0xFF0743A2),
    backgroundMid     = Color(0xFF128BC9),
    selectionGlow     = Color(0x297EE8FF),
    chromeTop         = Color(0xFF0743A2),
    chromeBottom      = Color(0xFF128BC9),
    chromeDivider     = Color(0xFF7EE8FF),
    categorySelected  = Color(0xFF006BC4),
    categorySelectedEdge = Color(0xFF7EE8FF),
    categoryInactive  = Color(0xFF0874BE),
    tileNormal        = Color(0xFF083880),
    tileSelected      = Color(0xFF0B4FAA),
    tileSelectedEdge  = Color(0xFF7EE8FF),
    tileSelectedInner = Color(0xFF4CCFFF),
    footerBackground  = Color(0xFF003C8F),
    footerDivider     = Color(0xFF68C9EB),
    textPrimary       = Color.White,
    textSecondary     = Color(0xFFD6EDF7),
    contentBackground = Color(0x00000000),
    railBackground    = Color(0x30004590),
    searchField       = Color(0xFF0A2E5A),
    searchBorder      = Color(0xFF68C9EB),
    overlayDim        = Color(0x99000000),
    menuPanel         = Color(0xF00A1E3D),
    menuRowSelected   = Color(0x347EE8FF),
    destructive       = Color(0xFFFF6B6B),
)

private fun resolveHueSource(accent: Color, wave: Color, backgroundBottom: Color): Color =
    when {
        accent.isVividHue() -> accent
        wave.isVividHue() -> wave
        else -> backgroundBottom
    }

private fun Color.isVividHue(): Boolean {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    return max - min >= 0.10f && max >= 0.30f
}

@Composable
fun deriveStorefrontColors(): StorefrontColors {
    val pfp = LocalPFPColors.current
    return remember(pfp) { storefrontColorsFor(pfp) }
}

fun storefrontColorsFor(pfp: PFPColors): StorefrontColors {
    val hue = resolveHueSource(pfp.accentColor, pfp.waveColor, pfp.backgroundBottom)

    val accentEdge = lerp(hue, Color.White, 0.55f)
    val accentInner = lerp(hue, Color.White, 0.32f)

    val bgTop = pfp.backgroundTop
    val bgBottom = pfp.backgroundBottom

    val (backgroundDeep, backgroundMid) = xmbScrimAnchors(bgTop, bgBottom)

    val chromeTop = bgTop.copy(alpha = 0.96f)
    val chromeBottom = lerp(bgTop, bgBottom, 0.55f).copy(alpha = 0.96f)

    val categorySelected = lerp(bgBottom, hue, 0.35f).copy(alpha = 0.92f)
    val categoryInactive = bgTop.copy(alpha = 0.50f)

    val tileNormal = bgTop.copy(alpha = 0.85f)
    val tileSelected = lerp(bgTop, hue, 0.35f).copy(alpha = 0.95f)

    val footerBackground = lerp(bgTop, Color.Black, 0.15f).copy(alpha = 0.98f)
    val footerDivider = lerp(hue, Color.White, 0.25f)

    val textPrimary = ensureReadable(Color.White, backgroundMid, 3.0f)
    val lightChrome = textPrimary == Color.Black
    val textSecondary = ensureReadable(
        fg = if (lightChrome) lerp(Color.Black, Color.White, 0.25f)
        else lerp(hue, Color.White, 0.72f),
        bg = backgroundMid,
        minContrast = 3.0f,
    )
    val edge = if (lightChrome) lerp(hue, Color.Black, 0.45f) else accentEdge
    val edgeInner = if (lightChrome) lerp(hue, Color.Black, 0.20f) else accentInner

    val contentBackground = Color(0x00000000)
    val railBackground = bgTop.copy(alpha = 0.35f)

    val searchField = if (lightChrome) Color.White.copy(alpha = 0.30f)
    else lerp(bgTop, Color.Black, 0.35f).copy(alpha = 0.90f)
    val searchBorder = edge

    val overlayDim = Color(0x99000000)
    val menuPanel = if (lightChrome) Color.White.copy(alpha = 0.92f)
    else lerp(Color.Black, bgTop, 0.30f).copy(alpha = 0.96f)
    val menuRowSelected = edge.copy(alpha = 0.20f)

    return StorefrontColors(
        accentHue           = hue,
        backgroundDeep      = backgroundDeep,
        backgroundMid       = backgroundMid,
        selectionGlow       = (if (lightChrome) lerp(hue, Color.Black, 0.55f)
        else lerp(hue, Color.White, 0.45f)).copy(alpha = 0.16f),
        chromeTop           = chromeTop,
        chromeBottom        = chromeBottom,
        chromeDivider       = edge,
        categorySelected    = categorySelected,
        categorySelectedEdge = edge,
        categoryInactive    = categoryInactive,
        tileNormal          = tileNormal,
        tileSelected        = tileSelected,
        tileSelectedEdge    = edge,
        tileSelectedInner   = edgeInner,
        footerBackground    = footerBackground,
        footerDivider       = footerDivider,
        textPrimary         = textPrimary,
        textSecondary       = textSecondary,
        contentBackground   = contentBackground,
        railBackground      = railBackground,
        searchField         = searchField,
        searchBorder        = searchBorder,
        overlayDim          = overlayDim,
        menuPanel           = menuPanel,
        menuRowSelected     = menuRowSelected,
        destructive         = Color(0xFFFF6B6B),
    )
}
