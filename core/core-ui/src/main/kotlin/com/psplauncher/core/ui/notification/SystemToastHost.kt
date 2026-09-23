package com.psplauncher.core.ui.notification

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Draws finished background work as a pill across the top centre, one at a time, then lets it go.
 *
 * It started in the top left, where the console this launcher is imitating puts its notifications,
 * and that corner turned out to be the one place on the XMB with something in it: the item ABOVE
 * the cursor sits there on every column page. The top centre is the only band that is reliably
 * empty -- the clock and the status icons hold the two ends of the strip, the controller prompts
 * hold the bottom, and the launch spine holds the right edge.
 *
 * Queued rather than stacked. Two scans finishing together produce two pills in sequence, not a
 * column of them: a stack has no natural height limit and this screen is 462dp tall.
 *
 * Nothing here is remembered across process death, and that is correct -- a toast that survives a
 * restart to tell you about a scan from before it would be reporting on a world that no longer
 * exists.
 */
@Composable
fun SystemToastHost(modifier: Modifier = Modifier) {
    val queue = remember { emptyList<SystemToast>().toMutableStateList() }

    LaunchedEffect(Unit) {
        SystemToasts.events.collect { queue.add(it) }
    }

    val current = queue.firstOrNull()
    // Two states, not one: `shown` drives the exit animation, so the pill slides out before it is
    // dropped from the queue rather than vanishing the instant its time is up.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(current?.id) {
        val toast = current ?: return@LaunchedEffect
        shown = true
        delay(if (toast.kind == ToastKind.ERROR) ErrorDwellMs else DwellMs)
        shown = false
        delay(ExitMs.toLong())
        queue.remove(toast)
    }

    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = shown && current != null,
            // Down and back up, now that it is centred: a pill that slid in from the left edge
            // while sitting in the middle would travel across the page to get to its own spot.
            enter = slideInVertically(tween(EnterMs)) { -it } + fadeIn(tween(EnterMs)),
            exit = slideOutVertically(tween(ExitMs)) { -it } + fadeOut(tween(ExitMs)),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = TopGap),
        ) {
            // The queue is not popped until ExitMs after `shown` goes false, so the toast this
            // reads is still there for the whole slide out.
            current?.let { ToastPill(it) }
        }
    }
}

@Composable
private fun ToastPill(toast: SystemToast) {
    val accent = if (toast.kind == ToastKind.ERROR) ErrorTint else SuccessTint
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .widthIn(max = MaxWidth)
            .clip(PillShape)
            .background(PillFill)
            .border(1.dp, PillEdge, PillShape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        // A drawn glyph rather than a vector asset: core-ui has no material-icons dependency, and
        // pulling one in so a pill can show a tick would be the largest thing in this file.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(16.dp).clip(RoundedCornerShape(50)).background(accent.copy(alpha = 0.18f)),
        ) {
            Text(
                text = if (toast.kind == ToastKind.ERROR) "!" else "\u2713",
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = toast.title,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (toast.message != null) {
                Text(
                    text = toast.message,
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// A failure is worth reading twice; a success is worth glancing at.
private const val DwellMs = 3200L
private const val ErrorDwellMs = 5200L
private const val EnterMs = 260
private const val ExitMs = 200

private val MaxWidth = 260.dp
// Clear of the status strip, which is 18dp tall and owns the very top of the screen.
private val TopGap = 26.dp
private val PillShape = RoundedCornerShape(9.dp)
private val PillFill = Color(0xE60D0D14)
private val PillEdge = Color(0x1AFFFFFF)
private val SuccessTint = Color(0xFF6FD08C)
private val ErrorTint = Color(0xFFE2606A)
