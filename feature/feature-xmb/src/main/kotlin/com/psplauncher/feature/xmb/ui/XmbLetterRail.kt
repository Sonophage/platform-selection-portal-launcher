package com.psplauncher.feature.xmb.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.theme.LocalPfpTextColors
import com.psplauncher.core.ui.theme.deriveStorefrontColors
import com.psplauncher.feature.xmb.viewmodel.LetterJumpState
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import com.psplauncher.feature.xmb.viewmodel.letterAnchors

// ── The A–Z rail ──────────────────────────────────────────────────────────────
//
// A column of the letters this list actually contains, down the right edge. Hold a shoulder and
// it lights up and the D-pad walks it; drag a finger down it and it does the same thing. Both
// gestures end the same way — let go, and wherever the rail left the cursor is where you are.
//
// It is drawn here rather than inside XMBItemList because it is chrome beside the column, not
// part of it: the list already takes thirty parameters, and the rail needs none of them except
// the rows themselves.
//
// PRESENT BUT QUIET is the resting state, and that is the touch half of the feature. A rail that
// appeared only once you were already holding a shoulder would be a control touch could never
// find — there is nothing to hold. So it sits at low alpha whenever the list has one, which also
// makes it the only thing on screen that says this list is long enough to scrub.

internal val RAIL_WIDTH = 26.dp
private val RESTING_ALPHA = 0.28f

/**
 * Every rung is this tall, whichever size its glyph is drawn at.
 *
 * Set explicitly, and the same for both states, for two reasons. Without it a Text inherits the
 * theme's body line height — 24sp — so a 10sp letter occupied 25dp, and eighteen rungs ran from
 * the clock to the hint bar. And a height that followed the font size would make the rail grow
 * and shift under a thumb whenever the cursor moved onto a rung, which is the one thing a
 * scrubber must not do.
 *
 * The number is what has to FIT, and it is set in sp, so it grows with the user's text size while
 * the two chrome bands stay fixed in dp. Worst case is 27 rungs — '#' plus A to Z — in
 * 462 - 34 - 34 = 394dp, less this rail's own 12dp of padding.
 *
 *     27 x 10sp          = 270dp   fits at normal text size
 *     27 x 10sp x 1.3    = 351dp   fits for someone using larger text
 *     ceiling            ~ 1.41x   (394 - 12) / 270 — past that a full A-Z rail clips
 *
 * 13sp was the first answer here and it was wrong: it fits at 1.0 and needs 468dp at 1.3x, so the
 * first user to enlarge their text would have lost the bottom of the alphabet — silently, because
 * the rail simply draws past the bar. XmbLetterRailFitTest is the sum, and it goes red if any of
 * the three numbers moves without the others.
 *
 * Above ~1.4x a library holding all 27 initials still clips. That is a real limit and it is
 * written down rather than hidden; the fix if it ever matters is to drop rungs rather than shrink
 * them, since an index that cannot be read is worse than one that is coarser.
 */
internal val RUNG_LINE_HEIGHT = 10.sp

/** The rail's own breathing room, inside the pill. Named so the fit test can add it up. */
internal val RAIL_VERTICAL_PADDING = 6.dp

@Composable
fun XmbLetterRail(
    items: List<XMBItem>,
    letterJump: LetterJumpState?,
    onTouch: (Float) -> Unit,
    onReleased: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Computed from the rows, once per list, in the one place that already has them. No second
    // copy of "does this list have a rail" in the state for the UI to read and drift from.
    val anchors = remember(items) { letterAnchors(items) }
    if (anchors == null) return

    val colors = deriveStorefrontColors()
    val text = LocalPfpTextColors.current
    val live by animateFloatAsState(
        targetValue = if (letterJump != null) 1f else 0f,
        animationSpec = tween(140),
        label = "letterRailLive",
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(RAIL_WIDTH)
            // The whole column is the target, and the gesture is read as a position rather than
            // as a hit on a particular letter: a rung is RUNG_LINE_HEIGHT tall, well under the
            // recommended touch minimum, so aiming at one would miss. A fraction of the rail's
            // height cannot miss — it always resolves to the nearest rung.
            .pointerInput(anchors) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val height = size.height.toFloat().coerceAtLeast(1f)
                    onTouch(down.position.y / height)
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        onTouch(change.position.y / height)
                        change.consume()
                    }
                    onReleased()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            // No gap: the rungs' own line height is the pitch, and a spacer between them would
            // be a second number to keep in step with the arithmetic on RUNG_LINE_HEIGHT.
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(RAIL_WIDTH / 2))
                .background(colors.menuPanel.copy(alpha = 0.55f * live))
                .padding(vertical = RAIL_VERTICAL_PADDING),
        ) {
            anchors.forEachIndexed { index, anchor ->
                val active = letterJump != null && letterJump.cursor == index
                Text(
                    text = anchor.letter.toString(),
                    color = if (active) text.primary else text.secondary,
                    // The active rung is marked by weight, colour and the rail's own brightening
                    // — NOT by a larger glyph. A bigger active letter inside a fixed line box
                    // clips; one that changed the box would shift every rung under the thumb
                    // that is scrubbing them.
                    fontSize = if (active) 10.sp else 9.sp,
                    lineHeight = RUNG_LINE_HEIGHT,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.alpha(RESTING_ALPHA + (1f - RESTING_ALPHA) * live),
                )
            }
        }
    }
}
