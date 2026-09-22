package com.psplauncher.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.theme.LocalPfpTextColors

// ── Idle controller hint pill ────────────────────────────────────────────────
//
// The small dark rounded pill the XMB and the App Drawer both use to name *actions* once the
// user has been idle: glyphs track the controller family and X/Y swap through
// [ControllerPromptBar], so the pill can never disagree with the pad. Lives in core-ui because
// two feature modules render it (feature-xmb's ContextMenuHint and the App Drawer's hint bar) and
// features must not depend on each other.

/**
 * The gap from the screen edge to a hint pill.
 *
 * Here rather than at each surface because the pill sits in the same corner on the XMB, in the
 * App Drawer and on every settings screen, and "the same corner" is the whole point: two copies
 * of the number with a comment saying they must match is how it stops being the same corner.
 */
val ControllerHintEdgeGap = 5.dp

/**
 * A rounded black pill of controller prompts, faded in by the caller when the user has been idle.
 *
 * Surface-level chrome ([shape], [background], [arrangement]) is parameterized; the inner prompt
 * look is fixed — 14sp SemiBold white labels with the classic drop shadow and 20dp glyphs — so
 * every pill in the app reads as one system. Renders nothing for an empty [items] (the caller may
 * skip the call instead — both are safe).
 */
@Composable
fun ControllerHintBar(
    items: List<ControllerPromptItem>,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(6.dp),
    background: Color = Color.Black.copy(alpha = 0.5f),
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(9.dp),
    /** Forwarded to [ControllerPromptBar]: non-null makes the single-action prompts tappable. */
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    ControllerPromptBar(
        items = items,
        onAction = onAction,
        modifier = modifier
            .background(
                color = background,
                shape = shape,
            )
            // Tight to the glyphs. The pill's height is its content's now that the prompts no
            // longer reserve a 48dp touch target (see ControllerPromptBar), so this padding is
            // the only thing between the text and the fill's edge.
            .padding(horizontal = 5.dp, vertical = 2.dp),
        labelColor = Color.White,
        labelStyle = TextStyle(
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.75f),
                offset = Offset(0f, 2f),
                blurRadius = 4f,
            ),
        ),
        glyphSize = 11.dp,
        arrangement = arrangement,
    )
}

// ── The two looks a prompt row is allowed to have ────────────────────────────
//
// Before this there were as many looks as there were call sites. Fifteen screens each passed
// their own labelColor, labelStyle, glyphSize and arrangement to ControllerPromptBar, which
// between them came to NINE near-identical greys, three font sizes (10, 11, 12sp), three glyph
// sizes (14, 15, 16dp) and three spacings (14, 16, 18dp). Nobody chose that; it is what happens
// when every screen answers the same question for itself. It is also exactly the complaint —
// the prompts look different in a menu.
//
// So the numbers move here and the call sites choose a STYLE instead. Two is the real count:
// a pill floating over content, and a bare row sitting inside a screen's own chrome.

/** Which of the two looks a row of prompts wears. */
enum class ControllerHintStyle {
    /**
     * A black rounded pill over content — the corner legend on the XMB, the App Drawer, the
     * detail pages. Loud on purpose: it sits on artwork it does not control.
     */
    PILL,

    /**
     * A bare row inside a screen's own chrome — menus, pickers, settings. No pill and no shadow:
     * the surface under it is already the app's, so the prompts sit in it rather than on top of
     * it, and they take the theme's own secondary text colour like every other label there.
     */
    INLINE,

    /**
     * A bare row over a dark media scrim — the photo viewer's gradient, the video player, the
     * Artwork Studio, the overlays drawn on top of the live crossbar.
     *
     * Separate from [INLINE] because the theme cannot answer for these. Their background is black
     * whatever scheme the user picked, so a themed secondary colour would come out dark on black
     * the moment they choose a pale one — the inverse of the bug that made INLINE theme-aware in
     * the first place. Fixed light grey, because the surface is fixed dark.
     */
    OVERLAY,
}

/**
 * INLINE's label colour: the theme's own secondary text, not a fixed grey.
 *
 * A fixed white-at-70% was tried first and was wrong on the device. These rows sit on the app's
 * own chrome, and on a pale scheme that chrome is light — the hints came out barely legible on
 * the search screen. The theme already answers this question for every other label in the app
 * (see PFPTheme), and nine hardcoded greys were the problem here, so a tenth is not the fix.
 */
/** OVERLAY's label colour: light, because what it sits on is dark regardless of the theme. */
private val OverlayLabel = Color.White.copy(alpha = 0.72f)

private val inlineLabelColor: Color
    @Composable @ReadOnlyComposable get() = LocalPfpTextColors.current.secondary

/**
 * A row of controller prompts in one of the app's two looks.
 *
 * This is the only thing a feature module should call. [ControllerPromptBar] takes the look as
 * parameters and is internal for that reason: a public knob is an invitation to invent a tenth
 * grey.
 */
@Composable
fun PfpControllerHints(
    items: List<ControllerPromptItem>,
    style: ControllerHintStyle,
    modifier: Modifier = Modifier,
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    when (style) {
        ControllerHintStyle.PILL -> ControllerHintBar(items, modifier, onAction = onAction)
        ControllerHintStyle.INLINE, ControllerHintStyle.OVERLAY -> ControllerPromptBar(
            items = items,
            onAction = onAction,
            modifier = modifier,
            labelColor = if (style == ControllerHintStyle.OVERLAY) OverlayLabel else inlineLabelColor,
            labelStyle = TextStyle(fontSize = 12.sp),
            glyphSize = 16.dp,
            // Centred, which is what most of the rows this replaces were already doing, and what
            // the rest read as once they are all the same row.
            arrangement = Arrangement.spacedBy(18.dp, androidx.compose.ui.Alignment.CenterHorizontally),
        )
    }
}
