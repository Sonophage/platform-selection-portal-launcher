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

private val RAIL_WIDTH = 26.dp
private val RESTING_ALPHA = 0.28f

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
            // as a hit on a particular letter: a rung is about 14dp tall, which is under the
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
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(RAIL_WIDTH / 2))
                .background(colors.menuPanel.copy(alpha = 0.55f * live))
                .padding(vertical = 6.dp),
        ) {
            anchors.forEachIndexed { index, anchor ->
                val active = letterJump != null && letterJump.cursor == index
                Text(
                    text = anchor.letter.toString(),
                    color = if (active) text.primary else text.secondary,
                    fontSize = if (active) 13.sp else 10.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.alpha(RESTING_ALPHA + (1f - RESTING_ALPHA) * live),
                )
            }
        }
    }
}
