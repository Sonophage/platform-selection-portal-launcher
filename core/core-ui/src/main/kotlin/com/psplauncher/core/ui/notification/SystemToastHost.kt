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
import androidx.compose.ui.draw.shadow
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
        horizontalArrangement = Arrangement.spacedBy(ToastStyle.Gap),
        modifier = Modifier
            .widthIn(max = ToastStyle.MaxWidth)
            // shadow() clips to the shape itself at any non-zero elevation, so this is the clip
            // as well as the lift — a second .clip() under it would be doing nothing.
            .shadow(ToastStyle.Lift, ToastStyle.Shape)
            .background(ToastStyle.Fill)
            .border(1.dp, ToastStyle.Hairline, ToastStyle.Shape)
            .padding(horizontal = ToastStyle.PadH, vertical = ToastStyle.PadV),
    ) {
        // The leading slot the design fills with the cover of whatever the toast is about.
        //
        // Nothing can fill it yet: every toast the launcher posts is the outcome of a background
        // TASK -- an artwork scrape, an export, a migration, a media scan -- and none of those is
        // about one game with one piece of art. The design's own example, "State saved · slot 3 ·
        // Pokémon Unbound", is a toast this app does not emit. So the slot keeps the outcome
        // glyph that was already here, at the size and corner the cover would use, and the day a
        // per-item sender exists the art drops straight in without moving anything.
        //
        // A drawn glyph rather than a vector asset: core-ui has no material-icons dependency, and
        // pulling one in so a pill can show a tick would be the largest thing in this file.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(ToastStyle.LeadingSlot)
                .clip(RoundedCornerShape(ToastStyle.LeadingCorner))
                .background(accent.copy(alpha = 0.18f)),
        ) {
            Text(
                text = if (toast.kind == ToastKind.ERROR) "!" else "\u2713",
                color = accent,
                fontSize = ToastStyle.GlyphSize,
                fontWeight = FontWeight.Bold,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = toast.title,
                color = Color.White,
                fontSize = ToastStyle.TitleSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (toast.message != null) {
                Text(
                    text = toast.message,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = ToastStyle.MessageSize,
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

// Clear of the status strip, which is 18dp tall and owns the very top of the screen.
private val TopGap = 26.dp
private val SuccessTint = Color(0xFF6FD08C)
private val ErrorTint = Color(0xFFE2606A)

/**
 * The card, as the redesign draws it.
 *
 * "I just want the toast to look like this but let it stay where it is" — so every number here
 * moved and the position did not. It is still the top centre, still one at a time.
 *
 * The design specifies pixels on a 1920x1080 frame. This panel is 374dpi, a density of 2.3375, so
 * 1920x1080 is 821x462dp and every figure below is its pixel count divided by that. Kept as the
 * arithmetic rather than as round dp numbers, because the next panel will have a different
 * density and the pixel figure is the thing the design actually said.
 */
internal object ToastStyle {
    /**
     * The design's own legibility floor: "No text below 28 px at native res."
     *
     * Stated in its System block, and its own toast then sets the second line at 26px. The FLOOR
     * wins: it is the rule the whole bundle is drawn against, and 26 is a slip inside one card.
     * The visible cost is two pixels on one line; the cost of the other reading is a system rule
     * that means nothing the first time a mock disagrees with it.
     */
    const val LegibilityFloorPx = 28f

    /** This panel: 374dpi. 1080 physical pixels over 462dp. */
    const val PanelDensity = 2.3375f

    private fun px(p: Float) = (p / PanelDensity).dp

    /** 640px of a 1920px frame. */
    val MaxWidth = px(640f)
    val Shape = RoundedCornerShape(px(24f))
    val PadH = px(28f)
    val PadV = px(22f)
    val Gap = px(24f)

    /** The cover slot: 80px square, 16px corner. */
    val LeadingSlot = px(80f)
    val LeadingCorner = px(16f)

    /** 0 18px 40px rgba(0,0,0,.35), as near as an elevation gets to a CSS blur. */
    val Lift = px(40f) * 0.42f

    val Fill = Color(0xF0160902)
    val Hairline = Color(0x1FFFFFFF)

    val TitleSize = (32f / PanelDensity).sp
    val MessageSize = (LegibilityFloorPx / PanelDensity).sp
    val GlyphSize = (34f / PanelDensity).sp
}
