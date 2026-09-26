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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.components.XmbScrim
import com.psplauncher.core.ui.theme.LocalPfpTextColors
import com.psplauncher.feature.xmb.viewmodel.LetterJumpState
import com.psplauncher.feature.xmb.viewmodel.XMBItem
import com.psplauncher.feature.xmb.viewmodel.letterAnchors

internal val RAIL_WIDTH = 26.dp
private val RESTING_ALPHA = 0.28f

private const val RESTING_TAB_ALPHA = 0.55f
private const val LIVE_TAB_ALPHA = 0.90f
private val TAB_CORNER = 10.dp

internal val RUNG_LINE_HEIGHT = 10.sp

internal val RAIL_VERTICAL_PADDING = 6.dp

@Composable
fun XmbLetterRail(
    items: List<XMBItem>,
    letterJump: LetterJumpState?,
    onTouch: (Float) -> Unit,
    onReleased: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchors = remember(items) { letterAnchors(items) }
    if (anchors == null) return

    val text = LocalPfpTextColors.current
    val live by animateFloatAsState(
        targetValue = if (letterJump != null) 1f else 0f,
        animationSpec = tween(140),
        label = "letterRailLive",
    )
    val tab = RESTING_TAB_ALPHA + (LIVE_TAB_ALPHA - RESTING_TAB_ALPHA) * live

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(RAIL_WIDTH)

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

            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = TAB_CORNER, bottomStart = TAB_CORNER))
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Transparent,
                        0.45f to XmbScrim.copy(alpha = tab),
                        1f to XmbScrim.copy(alpha = tab),
                    ),
                )
                .padding(vertical = RAIL_VERTICAL_PADDING),
        ) {
            anchors.forEachIndexed { index, anchor ->
                val active = letterJump != null && letterJump.cursor == index
                Text(
                    text = anchor.letter.toString(),
                    color = if (active) text.primary else text.secondary,

                    fontSize = if (active) 10.sp else 9.sp,
                    lineHeight = RUNG_LINE_HEIGHT,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.alpha(RESTING_ALPHA + (1f - RESTING_ALPHA) * live),
                )
            }
        }
    }
}
