package com.psplauncher.core.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.psplauncher.core.ui.theme.LocalPFPColors
import com.psplauncher.core.ui.theme.PFPColors
import com.psplauncher.core.ui.theme.composite
import com.psplauncher.core.ui.theme.contrastRatio
import com.psplauncher.core.ui.theme.ensureReadable
import com.psplauncher.core.ui.theme.storefrontColorsFor

@Immutable
data class DetailPalette(
    val pageTop: Color,
    val pageBottom: Color,

    val header: Color,

    val divider: Color,

    val rowFill: Color,

    val rowEdge: Color,

    val track: Color,

    val focus: Color,
    val textPrimary: Color,
    val textMuted: Color,
)

fun detailPaletteFor(pfp: PFPColors): DetailPalette {
    val drawer = storefrontColorsFor(pfp)

    val lightChrome = drawer.textPrimary == Color.Black
    val rowFill = if (lightChrome) Color.White.copy(alpha = 0.30f)
    else lerp(pfp.backgroundTop, Color.Black, 0.30f).copy(alpha = 0.60f)

    val rowOnScreen = composite(rowFill, composite(drawer.backgroundDeep, Color.White))
    val textMuted = if (contrastRatio(drawer.textSecondary, rowOnScreen) >= MUTED_TEXT_CONTRAST) {
        drawer.textSecondary
    } else {
        ensureReadable(lerp(drawer.textPrimary, rowOnScreen, 0.18f), rowOnScreen, MUTED_TEXT_CONTRAST.toFloat())
    }
    return DetailPalette(
        pageTop = drawer.backgroundDeep,
        pageBottom = drawer.backgroundMid,
        header = Color.Transparent,
        divider = drawer.chromeDivider,
        rowFill = rowFill,
        rowEdge = drawer.chromeDivider.copy(alpha = 0.35f),
        track = drawer.chromeDivider.copy(alpha = 0.25f),
        focus = drawer.tileSelectedEdge,
        textPrimary = drawer.textPrimary,
        textMuted = textMuted,
    )
}

private const val MUTED_TEXT_CONTRAST = 3.0

@Volatile
private var cached: Pair<PFPColors, DetailPalette>? = null

@Composable
@ReadOnlyComposable
fun detailPalette(): DetailPalette {
    val pfp = LocalPFPColors.current
    cached?.let { (theme, palette) -> if (theme == pfp) return palette }
    return detailPaletteFor(pfp).also { cached = pfp to it }
}

val DetailHeroMinHeight: Dp = 120.dp

val DetailHeroBandBelow: Dp = 16.dp + 18.dp + 124.dp + 8.dp

val DetailActionMessageHeight: Dp = 20.dp

fun detailHeroHeightFor(viewport: Dp, messageLine: Boolean = false): Dp {
    val below = DetailHeroBandBelow + if (messageLine) DetailActionMessageHeight else 0.dp
    return (viewport - below).coerceIn(DetailHeroMinHeight, DetailHeroHeight)
}
