package com.psplauncher.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
    shape: Shape = RoundedCornerShape(10.dp),
    background: Color = Color.Black.copy(alpha = 0.5f),
    arrangement: Arrangement.Horizontal = Arrangement.spacedBy(16.dp),
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
            .padding(horizontal = 8.dp, vertical = 4.dp),
        labelColor = Color.White,
        labelStyle = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.75f),
                offset = Offset(0f, 2f),
                blurRadius = 4f,
            ),
        ),
        glyphSize = 20.dp,
        arrangement = arrangement,
    )
}
