package com.psplauncher.core.ui.components

import androidx.compose.foundation.background
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

// ── The bottom bar ────────────────────────────────────────────────────────────
//
// Full width, not a pill in the corner. Both ways to MOVE are on the left — back first, then the
// primary behind a divider — and everything else is on the right.
//
// It lives here rather than in feature-xmb because the crossbar is not the only screen with a
// footer: Search, the App Drawer and the game's page each grew one of their own, in three
// different looks, and feature-appbar cannot see feature-xmb to borrow the fourth.
//
// The split is read from the ACTIONS, not passed in as three named slots. A caller already knows
// which prompt is Back because it built it with GamepadAction.BACK; asking it to say so a second
// time is the pair that drifts. Prompts the bar cannot place — a D-pad legend, Prev/Next — fall to
// the right in the order they were given.

@Composable
fun PfpHintBar(
    items: List<ControllerPromptItem>,
    modifier: Modifier = Modifier,
    onAction: ((GamepadAction) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val (back, primary, right) = hintBarGroups(items)

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
        back?.let { Group(listOf(it), onAction) }
        primary?.let {
            if (back != null) Divider()
            Group(listOf(it), onAction)
        }

        Box(Modifier.weight(1f))

        Group(right, onAction)
    }
}

/**
 * One cluster of prompts.
 *
 * OVERLAY, not PILL. The app allows three prompt looks and no others — nine hardcoded greys and
 * three font sizes are what it had before that rule — and this bar is precisely what OVERLAY
 * describes: a bare row over a dark scrim on top of live content. It is also the bigger of the two
 * (12sp against the pill's 8sp).
 *
 * Going through the shared renderer is what keeps the bar honest: that is what knows the
 * controller family and the X/Y swap, so this bar can name an action but can never name the wrong
 * button for it.
 */
@Composable
private fun Group(items: List<ControllerPromptItem>, onAction: ((GamepadAction) -> Unit)?) {
    if (items.isEmpty()) return
    PfpControllerHints(items = items, style = ControllerHintStyle.OVERLAY, onAction = onAction)
}

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

/** Where each prompt sits: [back] and [primary] on the left, [right] on the right. */
internal data class HintBarGroups(
    val back: ControllerPromptItem?,
    val primary: ControllerPromptItem?,
    val right: List<ControllerPromptItem>,
)

/**
 * Which side of the bar each prompt belongs on, read from the action it already names.
 *
 * Every prompt lands somewhere. That is the property worth holding: the bar used to take three
 * named slots, which could hold a Back, a Select and a list — and a footer naming the D-pad as a
 * whole, or L1/R1 for Prev/Next page, has prompts that fit none of those three. Those were the
 * ones the crossbar's bar could not express, and they are exactly what the game's manual viewer
 * and the App Drawer are made of.
 *
 * Only a prompt naming EXACTLY ONE remappable button can take a left-hand slot, and that question
 * is [ControllerPromptItem.tappableAction]'s, not this function's. A range prompt — "◀▶ Seek" —
 * contains SELECT without being the confirm, and putting it in the primary slot would draw the
 * loudest position on the bar as something a tap does nothing to. Asking the same question a
 * second way here is how the two answers drift.
 *
 * Identity comparison, not equality: two prompts with the same action and label are a caller's
 * mistake, but dropping the second one silently would hide it.
 */
internal fun hintBarGroups(items: List<ControllerPromptItem>): HintBarGroups {
    val back = items.firstOrNull { it.tappableAction() == GamepadAction.BACK }
    val primary = items.firstOrNull { it.tappableAction() == GamepadAction.SELECT }
    return HintBarGroups(back, primary, items.filter { it !== back && it !== primary })
}

private val BarHeight = HintBarHeight
private val EdgeGap = 22.dp
private val GroupGap = 16.dp
private val DividerHeight = 15.dp
private val BarScrim = Color(0xB3060200)
