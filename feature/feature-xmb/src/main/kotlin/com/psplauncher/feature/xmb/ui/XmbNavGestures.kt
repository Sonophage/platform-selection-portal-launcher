package com.psplauncher.feature.xmb.ui

import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private val ITEM_STEP_DP = 64.dp

private val CATEGORY_STEP_DP = 100.dp

private val EDGE_DP = 32.dp
private val EDGE_COMMIT_DP = 48.dp

private val SWIPE_BACK_COMMIT_DP = 72.dp

private val FLING_DP_PER_S = 420f

private const val FLING_MAX_STEPS = 12

private const val FLING_STEPS_PER_RATIO = 1.6f

fun Modifier.xmbNavGestures(
    onStepCategory: (Int) -> Unit,
    onStepItem: (Int) -> Unit,
    onEdgeBack: () -> Unit,

    stepScale: Float = 1f,

    swipeBackEnabled: Boolean = false,
    onSwipeBack: () -> Unit = {},

): Modifier = pointerInput(stepScale, swipeBackEnabled) {
    val slop = viewConfiguration.touchSlop
    val itemStepPx = ITEM_STEP_DP.toPx() * stepScale
    val categoryStepPx = CATEGORY_STEP_DP.toPx() * stepScale
    val edgePx = EDGE_DP.toPx()
    val edgeCommitPx = EDGE_COMMIT_DP.toPx()
    val backCommitPx = SWIPE_BACK_COMMIT_DP.toPx()
    val flingPx = FLING_DP_PER_S * density

    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            val fromEdge = down.position.x <= edgePx
            var axis = Axis.NONE
            var acc = 0f
            var lockX = 0f
            var lockY = 0f
            val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }

            while (true) {
                val event = awaitPointerEvent()

                if (event.changes.count { it.pressed } > 1) axis = Axis.CANCELLED
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if (axis == Axis.CANCELLED) { change.consume(); continue }

                val d = change.positionChange()
                tracker.addPosition(change.uptimeMillis, change.position)

                when (axis) {
                    Axis.NONE -> {
                        lockX += d.x; lockY += d.y
                        if (abs(lockX) > slop || abs(lockY) > slop) {
                            axis = if (abs(lockX) >= abs(lockY)) Axis.HORIZONTAL else Axis.VERTICAL

                            acc = if (axis == Axis.HORIZONTAL) lockX else lockY
                            change.consume()
                        }
                    }
                    Axis.HORIZONTAL -> {
                        acc += d.x
                        change.consume()

                        if (!fromEdge && !swipeBackEnabled) {
                            val whole = consumeWholeSteps(acc, categoryStepPx)
                            if (whole != 0) {
                                onStepCategory(-whole)
                                acc -= whole * categoryStepPx
                            }
                        }
                    }
                    Axis.VERTICAL -> {
                        acc += d.y
                        change.consume()
                        val whole = consumeWholeSteps(acc, itemStepPx)
                        if (whole != 0) {
                            onStepItem(-whole)
                            acc -= whole * itemStepPx
                        }
                    }
                    Axis.CANCELLED -> Unit
                }
            }

            when {
                axis == Axis.HORIZONTAL && fromEdge && acc > edgeCommitPx -> onEdgeBack()
                axis == Axis.HORIZONTAL && swipeBackEnabled && commitsSwipeBack(acc, backCommitPx) ->
                    onSwipeBack()
                axis == Axis.VERTICAL -> {
                    val vy = tracker.calculateVelocity().y
                    val bonus = flingBonusSteps(vy, flingPx)
                    if (bonus != 0) onStepItem(bonus)
                }
                else -> Unit
            }
        }
    }
}

private enum class Axis { NONE, HORIZONTAL, VERTICAL, CANCELLED }

fun consumeWholeSteps(accumulated: Float, stepPx: Float): Int =
    (accumulated / stepPx).toInt()

fun flingBonusSteps(velocityPxPerS: Float, flingPx: Float): Int {
    val speed = abs(velocityPxPerS)
    if (speed <= flingPx) return 0
    val magnitude = (speed / flingPx * FLING_STEPS_PER_RATIO)
        .toInt()
        .coerceIn(1, FLING_MAX_STEPS)
    return if (velocityPxPerS < 0) magnitude else -magnitude
}

fun commitsSwipeBack(accumulatedX: Float, commitPx: Float): Boolean = accumulatedX <= -commitPx
