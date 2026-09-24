package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.components.ControllerHintStyle
import com.psplauncher.core.ui.components.ControllerPromptItem
import com.psplauncher.core.ui.components.PfpControllerHints
import com.psplauncher.feature.xmb.viewmodel.XmbPrompt
import com.psplauncher.feature.xmb.viewmodel.XmbPrompts

// ── The bottom bar, 12g ───────────────────────────────────────────────────────
//
// Full width, not a pill in the corner. The primary action NAMES WHAT IT ACTS ON — "Open All
// Games" — with B beside it behind a divider, so both ways to move are on the left; everything
// else is on the right, and what is on the right follows whatever owns the screen.
//
// The prompts themselves are the shared ControllerHintBar with its fill taken away. That renderer
// is what knows the controller family and the X/Y swap, so this bar can name an action but can
// never name the wrong button for it — and drawing the glyphs here instead would be a second
// answer to a question the app has already settled twice.

@Composable
fun XmbHintBar(
    prompts: XmbPrompts,
    modifier: Modifier = Modifier,
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(BarHeight)
            // A wash rather than a filled bar: it sits over the wallpaper like the rest of the
            // chrome, and the prompts need separation from artwork, not a floor to stand on.
            .background(Brush.verticalGradient(0f to Color.Transparent, 1f to BarScrim))
            .padding(horizontal = EdgeGap),
    ) {
        // BACK FIRST. The design puts the primary first; the owner's call is the other way, and
        // it is the better one on a handheld: the button you press to get out of somewhere is the
        // one you want to find without reading, and it is in the same place on every screen —
        // whereas the primary's label changes with every row the cursor touches.
        Group(listOf(prompts.back), onAction)
        prompts.primary?.let { primary ->
            Divider()
            Group(listOf(primary), onAction)
        }

        Box(Modifier.weight(1f))

        Group(prompts.right, onAction)
    }
}

/**
 * One cluster of prompts.
 *
 * The target rides IN the label — "Open All Games" — rather than as a second Text beside it. The
 * design draws the row's name louder than the verb, and that is worth less than having one
 * renderer for every prompt in the app: a hand-drawn glyph here would be the one place that could
 * disagree with the pad.
 */
@Composable
private fun Group(prompts: List<XmbPrompt>, onAction: ((GamepadAction) -> Unit)?) {
    if (prompts.isEmpty()) return
    // OVERLAY, not PILL. The app allows three prompt looks and no others — nine hardcoded greys
    // and three font sizes are what it had before that rule — and this bar is precisely what
    // OVERLAY describes: a bare row over a dark scrim on top of the live crossbar. It is also the
    // bigger of the two (12sp against the pill's 8sp), which is the "little bigger" asked for,
    // without adding a fourth look to get it.
    PfpControllerHints(
        items = prompts.map { ControllerPromptItem(it.action, it.label()) },
        style = ControllerHintStyle.OVERLAY,
        onAction = onAction,
    )
}

private fun XmbPrompt.label(): String = target?.takeIf { it.isNotBlank() }?.let { "$verb  $it" } ?: verb

/** The rule between the primary and B — "B sits next to it, divided off". */
@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(horizontal = GroupGap)
            .width(1.dp)
            .height(DividerHeight)
            .background(Color.White.copy(alpha = 0.22f)),
    )
}

private val BarHeight = 34.dp
private val EdgeGap = 22.dp
private val GroupGap = 16.dp
private val DividerHeight = 15.dp
private val BarScrim = Color(0xB3060200)
