package com.psplauncher.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

data class LetterAnchor(val letter: Char, val index: Int)

const val LETTER_JUMP_MIN_ITEMS = 25

const val LETTER_JUMP_MIN_LETTERS = 3

fun initialOf(title: String): Char {
    val c = title.trimStart().firstOrNull() ?: return '#'
    return if (c.isLetter()) c.uppercaseChar() else '#'
}

fun letterAnchors(titles: List<String>): List<LetterAnchor>? {
    if (titles.size < LETTER_JUMP_MIN_ITEMS) return null

    val anchors = ArrayList<LetterAnchor>()
    var previous: Char? = null
    for ((index, title) in titles.withIndex()) {
        val letter = initialOf(title)
        if (previous != null && letter < previous) return null
        if (letter != previous) anchors.add(LetterAnchor(letter, index))
        previous = letter
    }
    return if (anchors.size < LETTER_JUMP_MIN_LETTERS) null else anchors
}

data class LetterJumpState(
    val anchors: List<LetterAnchor>,
    val cursor: Int = 0,

    val returnIndex: Int = 0,
) {
    val letter: Char get() = anchors[cursor].letter
    val targetIndex: Int get() = anchors[cursor].index
}

fun letterJumpFor(titles: List<String>, currentIndex: Int): LetterJumpState? {
    val anchors = letterAnchors(titles) ?: return null

    val cursor = anchors.indexOfLast { it.index <= currentIndex }.coerceAtLeast(0)
    return LetterJumpState(anchors = anchors, cursor = cursor, returnIndex = currentIndex)
}

fun LetterJumpState.move(delta: Int): LetterJumpState {
    val next = (cursor + delta).coerceIn(0, anchors.lastIndex)
    return if (next == cursor) this else copy(cursor = next)
}

fun LetterJumpState.at(rung: Int): LetterJumpState {
    val next = rung.coerceIn(0, anchors.lastIndex)
    return if (next == cursor) this else copy(cursor = next)
}

data class RailMetrics(
    val rungs: Int,
    val badge: Dp,
    val pitch: Dp,
    val glyph: TextUnit,
    val thickness: Dp,
)

internal val RailMinBadge = 16.dp
internal val RailMaxBadge = RailIcon
private val RungGap = 3.dp
private val BadgeSideGap = 4.dp
private const val GlyphRatio = 0.45f

val RAIL_PADDING = 6.dp

private const val ACTIVE_SCALE = 1.3f
private const val RESTING_ALPHA = 0.28f
private const val RESTING_TAB_ALPHA = 0.55f
private const val LIVE_TAB_ALPHA = 0.90f
private val TAB_CORNER = 10.dp

fun railMetrics(available: Dp, anchorCount: Int): RailMetrics {
    val usable = (available - RAIL_PADDING * 2).coerceAtLeast(RailMinBadge)
    val minPitch = RailMinBadge + RungGap
    val maxRungs = (usable / minPitch).toInt().coerceAtLeast(1)
    val rungs = anchorCount.coerceIn(1, maxRungs)
    val badge = (usable / rungs - RungGap).coerceIn(RailMinBadge, RailMaxBadge)
    return RailMetrics(
        rungs = rungs,
        badge = badge,
        pitch = badge + RungGap,
        glyph = (badge.value * GlyphRatio).sp,
        thickness = (badge + BadgeSideGap * 2) * 2,
    )
}

fun bucketIndices(anchorCount: Int, rungs: Int): List<Int> =
    if (anchorCount <= rungs) List(anchorCount) { it }
    else List(rungs) { (it * anchorCount.toFloat() / rungs).toInt() }

fun rungAt(along: Float, extent: Float, spanPx: Float, pitchPx: Float, rungCount: Int): Int {
    val lead = (extent - spanPx) / 2f
    return ((along - lead) / pitchPx).toInt().coerceIn(0, rungCount - 1)
}

@Composable
fun XmbLetterRail(
    titles: List<String>,
    cursor: Int?,
    onTouch: (Int) -> Unit,
    onReleased: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchors = remember(titles) { letterAnchors(titles) } ?: return

    BoxWithConstraints(modifier.fillMaxHeight()) {
        val metrics = railMetrics(maxHeight, anchors.size)
        val rungs = remember(anchors.size, metrics.rungs) { bucketIndices(anchors.size, metrics.rungs) }
        val live = railLive(cursor)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .width(metrics.thickness)
                .railGestures(rungs, metrics, vertical = true, onTouch = onTouch, onReleased = onReleased),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(RungGap, Alignment.CenterVertically),
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = TAB_CORNER, bottomStart = TAB_CORNER))
                    .background(
                        Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.45f to XmbScrim.copy(alpha = tabAlpha(live)),
                            1f to XmbScrim.copy(alpha = tabAlpha(live)),
                        ),
                    )
                    .padding(vertical = RAIL_PADDING, horizontal = BadgeSideGap),
            ) {
                Rungs(anchors, rungs, cursor, metrics, live)
            }
        }
    }
}

@Composable
fun XmbLetterBar(
    titles: List<String>,
    cursor: Int?,
    onTouch: (Int) -> Unit,
    onReleased: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val anchors = remember(titles) { letterAnchors(titles) } ?: return

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val metrics = railMetrics(maxWidth, anchors.size)
        val rungs = remember(anchors.size, metrics.rungs) { bucketIndices(anchors.size, metrics.rungs) }
        val live = railLive(cursor)

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.thickness)
                .railGestures(rungs, metrics, vertical = false, onTouch = onTouch, onReleased = onReleased),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(RungGap, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = TAB_CORNER, topEnd = TAB_CORNER))
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to XmbScrim.copy(alpha = tabAlpha(live)),
                            1f to XmbScrim.copy(alpha = tabAlpha(live)),
                        ),
                    )
                    .padding(horizontal = RAIL_PADDING, vertical = BadgeSideGap),
            ) {
                Rungs(anchors, rungs, cursor, metrics, live)
            }
        }
    }
}

@Composable
private fun railLive(cursor: Int?): Float {
    val live by animateFloatAsState(
        targetValue = if (cursor != null) 1f else 0f,
        animationSpec = tween(140),
        label = "letterRailLive",
    )
    return live
}

private fun tabAlpha(live: Float) = RESTING_TAB_ALPHA + (LIVE_TAB_ALPHA - RESTING_TAB_ALPHA) * live

@Composable
private fun Rungs(
    anchors: List<LetterAnchor>,
    rungs: List<Int>,
    cursor: Int?,
    metrics: RailMetrics,
    live: Float,
) {
    val activeRung = cursor?.let { c -> rungs.indexOfLast { it <= c }.coerceAtLeast(0) }
    rungs.forEachIndexed { rung, anchor ->
        val active = rung == activeRung
        RailLetterBadge(
            letter = anchors[anchor].letter,
            active = active,
            metrics = metrics,
            modifier = Modifier
                .zIndex(if (active) 1f else 0f)
                .alpha(if (active) 1f else RESTING_ALPHA + (1f - RESTING_ALPHA) * live),
        )
    }
}

@Composable
private fun RailLetterBadge(
    letter: Char,
    active: Boolean,
    metrics: RailMetrics,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(metrics.badge)
            .graphicsLayer {
                if (active) {
                    scaleX = ACTIVE_SCALE
                    scaleY = ACTIVE_SCALE
                }
            }
            .clip(RoundedCornerShape(metrics.badge * RailCornerRatio))
            .background(if (active) Color.White else Color.White.copy(alpha = 0.12f)),
    ) {
        Text(
            text = letter.toString(),
            color = if (active) RailInk else Color.White.copy(alpha = 0.85f),
            fontSize = metrics.glyph,
            fontWeight = FontWeight.Bold,
        )
    }
}

private val RailCornerRatio = RailCorner.value / RailIcon.value

private fun Modifier.railGestures(
    rungs: List<Int>,
    metrics: RailMetrics,
    vertical: Boolean,
    onTouch: (Int) -> Unit,
    onReleased: () -> Unit,
): Modifier = pointerInput(rungs, metrics, vertical) {
    val pitchPx = metrics.pitch.toPx()
    val spanPx = pitchPx * metrics.rungs - (metrics.pitch - metrics.badge).toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        report(down.position.let { if (vertical) it.y else it.x }, spanPx, pitchPx, rungs, vertical, onTouch)
        down.consume()
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            report(change.position.let { if (vertical) it.y else it.x }, spanPx, pitchPx, rungs, vertical, onTouch)
            change.consume()
        }
        onReleased()
    }
}

private fun PointerInputScope.report(
    along: Float,
    spanPx: Float,
    pitchPx: Float,
    rungs: List<Int>,
    vertical: Boolean,
    onTouch: (Int) -> Unit,
) {
    val extent = (if (vertical) size.height else size.width).toFloat()
    onTouch(rungs[rungAt(along, extent, spanPx, pitchPx, rungs.size)])
}
