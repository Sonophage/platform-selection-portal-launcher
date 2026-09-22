package com.psplauncher.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

// The one menu-cursor treatment every menu shares, derived from the SAME hue the App Drawer's
// selection edge is built from ([StorefrontColors.accentHue]) so Settings, the context menu and
// the drawer all tint together.
//
// They did not. Both of these lerped LocalPFPColors.accentColor toward white — but the theme sets
// accentColor to pure white (XmbColorScheme.resolve), and lerp(white, white) is white, so every
// menu cursor outside a game detail page was a plain neutral highlight while the drawer wore the
// theme's colour. storefrontColorsFor already resolved that case, by falling back to the wave when
// the accent carries no hue; reading its answer is what makes the three surfaces agree, and it is
// what lets the wallpaper-derived accent reach the menus at all.

/** Fill behind the focused menu row. */
@Composable
fun menuCursorFill(): Color =
    lerp(deriveStorefrontColors().accentHue, Color.White, 0.20f).copy(alpha = 0.34f)

/**
 * Bright edge/border of the focused menu row — the part that makes the cursor unmistakable.
 *
 * Literally the drawer's own selection edge rather than the same formula written out twice, so the
 * pale-theme branch (where that edge darkens toward the hue instead of lightening, to stay visible
 * on a light background) applies here too instead of being a fourth thing to keep in step.
 */
@Composable
fun menuCursorEdge(): Color = deriveStorefrontColors().tileSelectedEdge.copy(alpha = 0.95f)

/**
 * The shared focus-cursor for menu rows: accent-tinted fill plus a bright border. No-op when
 * [selected] is false, so it can sit unconditionally in a row's modifier chain.
 */
@Composable
fun Modifier.menuCursor(selected: Boolean, shape: Shape = RoundedCornerShape(8.dp)): Modifier {
    if (!selected) return this
    return this
        .clip(shape)
        .background(menuCursorFill())
        .border(1.5.dp, menuCursorEdge(), shape)
}
