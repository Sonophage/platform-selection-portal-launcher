package com.psplauncher.feature.xmb.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.ui.components.StatusStripHeight
import com.psplauncher.feature.xmb.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Status bar colours ────────────────────────────────────────────────────────

private val StripPrimary = Color(0xFFEEEEEE)
private val StripMuted   = Color(0xAAEEEEEE)
private val StripSep     = Color(0x55FFFFFF)

// ── Centralized asset mapping ─────────────────────────────────────────────────
//
// All status-bar drawables come from anthonycaccese/xmb-menu-es-de _inc/images/.
// Never reference these resource IDs outside this file; go through XmbStatusIcons.

object XmbStatusIcons {
    @DrawableRes val bluetooth: Int = R.drawable.ic_status_bluetooth

    /**
     * Tier thresholds live in [batterySlotKey] and the key→drawable table in [forSlotKey];
     * delegating through both keeps the mapping single-sourced — [forSlotKey]'s
     * compile-time-checked drawable refs plus DefaultSlotGlyphTest guard the pair.
     */
    @DrawableRes fun battery(level: Int, charging: Boolean): Int =
        requireNotNull(forSlotKey(batterySlotKey(level, charging))) {
            "batterySlotKey produced a key outside the status strip"
        }

    /** Themeable icon slot (theme-kit IconSlots key) matching [battery]'s tiers. */
    fun batterySlotKey(level: Int, charging: Boolean): String = when {
        charging       -> "status_battery_charging"
        level >= 76    -> "status_battery_full"
        level >= 51    -> "status_battery_high"
        level >= 26    -> "status_battery_medium"
        else           -> "status_battery_low"
    }

    /**
     * Built-in drawable behind a `status_*` slot key, or null when [slotKey] is not a status
     * slot. The icon customizer previews slot defaults through this rather than reaching for
     * the resource IDs directly, which stay private to this file.
     */
    @DrawableRes fun forSlotKey(slotKey: String): Int? = when (slotKey) {
        "status_bluetooth"         -> R.drawable.ic_status_bluetooth
        "status_battery_charging"  -> R.drawable.ic_status_battery_charging
        "status_battery_full"      -> R.drawable.ic_status_battery_full
        "status_battery_high"      -> R.drawable.ic_status_battery_high
        "status_battery_medium"    -> R.drawable.ic_status_battery_medium
        "status_battery_low"       -> R.drawable.ic_status_battery_low
        else                       -> null
    }
}

/**
 * What the left of the strip is doing right now.
 *
 * 12b: "the live activity anchors top-left". [art] is anything Coil can draw — a track's cover, a
 * game's icon — or null for a row that has no picture of its own.
 */
data class StripLiveActivity(val art: Any?, val title: String, val detail: String?)

/** Which navigation hints the centre offers. Both can be true; neither is the usual case. */
data class StripHints(val shoulder: Boolean = false, val leftRight: Boolean = false)

// ── The status strip, 12b ────────────────────────────────────────────────────
//
// Layout:  [art] TITLE / detail        <centre>        [icons]  TIME
//                                                                ▔▔▔▔  battery
//
// Pulled apart from the old one, which packed date, time, a separator and the whole icon set into
// the left corner and hung the battery percentage off the right. The live activity now owns the
// left, and the time owns the right with the battery drawn as a line under it rather than a glyph
// and a number beside it.
//
// Sized to THIS screen rather than to the design's own frame: the mock's band is about 85dp tall
// on a 1920x1080 sheet, which is a fifth of this panel's height. 28dp is what the two lines and
// the art tile actually need, against the 18dp the strip had before.

@Composable
fun XmbPspStatusStrip(
    sortLabel: String? = null,
    // When the last input was touch, the sort label becomes a tappable chip that cycles the sort
    // order; on controller it stays a plain label (X / Square cycles it).
    showSortButton: Boolean = false,
    onSortTapped: () -> Unit = {},
    /** The thing that is running, drawn top-left. Null when nothing is. */
    live: StripLiveActivity? = null,
    /** Which of the two navigation hints apply here. Drawn centre, when nothing else is. */
    hints: StripHints = StripHints(),
    /** Pressing the left half pulls the notifications down. Null leaves the corner inert. */
    onLiveAreaTapped: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * What sits in the middle of the bar, centred on the SCREEN.
     *
     * The home shelf's media filter lives here. It used to sit at the bottom-left under the
     * artwork, where it was one more thing stacked in a corner that already had the title and
     * the cards; a set of names you step through belongs with the clock and the battery, in the
     * band that is chrome rather than content.
     */
    centre: (@Composable BoxScope.() -> Unit)? = null,
) {
    val context = LocalContext.current
    var batteryLevel   by remember { mutableIntStateOf(0) }
    var isCharging     by remember { mutableStateOf(false) }
    var dateString     by remember { mutableStateOf(currentDateString(context)) }
    var timeString     by remember { mutableStateOf(currentTimeString(context)) }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val level  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale  = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                batteryLevel = if (level >= 0 && scale > 0) (level * 100 / scale) else 0
                isCharging   = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                               status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            dateString = currentDateString(context)
            timeString = currentTimeString(context)
            // Sleep to the next minute boundary rather than a flat 30 s, so the displayed minute
            // is never up to half a minute stale -- and so the strip wakes 2 times a minute at
            // worst instead of on a rhythm unrelated to what it shows.
            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    // Three slots, not two. The centre one is where the home shelf puts its filter names, so the
    // bar is a Box with left/centre/right rather than a SpaceBetween row: SpaceBetween would
    // centre the middle child between its neighbours, which moves every time the clock's width
    // or the icon set changes, and a row of tab names that drifts is worse than one that is off
    // centre by design.
    // The same wash the hint bar wears, upside down: opaque at the edge, gone by the band's
    // bottom. The strip used to be drawn only over the XMB, whose wave is dark, so it needed
    // nothing behind it. It is over the App Drawer, Settings, Search and a game's full-bleed hero
    // artwork now, and a white clock on a game's white banner is a clock nobody can read. One
    // band was guarded and the other was not.
    Box(
        modifier
            .fillMaxWidth()
            .height(StripHeight)
            .background(Brush.verticalGradient(0f to StripScrim, 1f to Color.Transparent)),
    ) {

        // The battery, as one hairline across the very top edge of the screen. Full bleed: it is
        // outside the content's horizontal padding on purpose, because a line that stops 20dp
        // short of each corner reads as a widget and this is meant to read as the edge itself.
        //
        // It shimmers while charging. That is the whole charging cue now — the bolt beside the
        // clock is gone, and a line that moves says "going up" without spending any of the clock's
        // room to say it.
        BatteryLine(
            level = batteryLevel,
            charging = isCharging,
            modifier = Modifier.align(Alignment.TopCenter),
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = BatteryLineHeight)
            .padding(horizontal = 20.dp),
    ) {
    Row(
        modifier = Modifier.align(Alignment.CenterStart),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // ── Left: what is running ─────────────────────────────────────────
        //
        // Music today, and only music. The design's other example is a download with a byte
        // count, and this app has no downloads — its background work is scans, scrapes, imports
        // and exports, none of which publishes progress anywhere the UI can read. When one does,
        // it becomes a second source for this same slot and nothing here changes shape.
        //
        // The date went with the redesign. It was beside the time in the old left corner, and the
        // right side of this one is the time alone under its battery line; a date squeezed in
        // there would be the thing that made the corner busy again.
        live?.let { activity ->
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = if (onLiveAreaTapped != null) {
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onLiveAreaTapped)
                } else {
                    Modifier
                },
            ) {
                // No tile when there is no art. An empty rounded square reads as a picture that
                // failed to load, and the two things that fill this slot without one — a finished
                // task and a bare count — have no picture to fail.
                if (activity.art != null) {
                    AsyncImage(
                        model = activity.art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(LiveArtSize)
                            .clip(RoundedCornerShape(LiveArtCorner)),
                    )
                }
                // BOTH lines carry an explicit lineHeight. Without one they inherit the ambient
                // text style's, which on this theme is 24sp — so an 8sp title occupied a 57px box
                // and the detail under it was measured into 6px and drawn as a smear. It looked
                // exactly like a strip too short for two lines, and the strip was not the problem.
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        activity.title,
                        color = StripPrimary,
                        fontSize = StripFontSize,
                        lineHeight = StripFontSize * 1.25f,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = LiveTextMax),
                    )
                    activity.detail?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            it,
                            color = StripMuted,
                            fontSize = LiveDetailSize,
                            lineHeight = LiveDetailSize * 1.25f,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = LiveTextMax),
                        )
                    }
                }
            }
        }

        }

        // ── Centre: which cut of this list you are looking at ──────────────
        //
        // The home shelf's media filters, or on every other column the sort mode. ONE slot,
        // because they answer the same question, and the shelf having its answer centred while
        // every other column had its answer tucked beside the clock made the two read as
        // unrelated things.
        //
        // The filters win where both could apply: X cycles the filter on that page, not the sort.
        when {
            // The caller's own content wins. On the home shelf that is the media filter, which is
            // STATE — which cut of the shelf you are looking at — and state beats a hint about a
            // button. Everywhere else the slot is free and the hints take it.
            centre != null -> centre.invoke(this)
            hints.shoulder || hints.leftRight -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    if (hints.shoulder) StripHint("LB  RB")
                    if (hints.leftRight) StripHint("◀  ▶")
                }
            }
            sortLabel != null -> {
                // Touch: a chip that cycles the sort. Controller: a plain label, because X
                // already does it and a chip would be a button that cannot be reached.
                if (showSortButton) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0x24FFFFFF))
                            .clickable(onClick = onSortTapped)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    ) {
                        Text("⇅", color = StripPrimary, fontSize = StripFontSize, fontWeight = FontWeight.Medium)
                        Text(sortLabel, color = StripPrimary, fontSize = StripFontSize, fontWeight = FontWeight.Medium)
                    }
                } else {
                    Text(
                        sortLabel,
                        color = StripPrimary,
                        fontSize = StripFontSize,
                        lineHeight = StripFontSize * 1.25f,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        // ── Right: [controller] [BT] [WiFi] [Signal] [Battery] ─────────────
        // Every status icon except battery is conditional: shown only when that hardware is
        // present/active (controller connected, Bluetooth on, Wi-Fi connected, cellular service),
        // and Wi-Fi/Signal reflect live strength. Battery is always shown.
        val sys = rememberSystemStatus()
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (sys.controllerConnected) {
                Icon(
                    imageVector        = Icons.Filled.SportsEsports,
                    contentDescription = "Controller connected",
                    tint               = StripMuted,
                    modifier           = Modifier.size(StripIconSize),
                )
            }
            if (sys.bluetoothOn) {
                StatusIcon(
                    XmbStatusIcons.bluetooth, "Bluetooth",
                    Modifier.size(width = StripIconSize * 0.7f, height = StripIconSize),
                    slotKey = "status_bluetooth",
                )
            }
            sys.wifiLevel?.let { level ->
                WifiMeter(level, Modifier.size(width = StripIconSize * 1.23f, height = StripIconSize))
            }
            sys.cellularLevel?.let { level ->
                SignalBars(level, Modifier.size(width = StripIconSize * 1.08f, height = StripIconSize))
            }
            // The time, with the battery as a LINE under it — 12b's right-hand pair. The glyph
            // and the "81%" beside it are gone: the line says the same thing in the space the
            // clock already occupies, and the bolt says the rest.
            //
            // Green while charging, white off it, and the low-battery tint still wins over both
            // because a line at 8% that is merely short is not a warning.
            // The clock, alone. The battery is the line at the top of the screen and the bolt is
            // the shimmer on it, so nothing else needs to be in this corner.
            Text(
                text       = timeString,
                color      = StripPrimary,
                fontSize   = StripFontSize,
                lineHeight = StripFontSize * 1.25f,
                fontWeight = FontWeight.Medium,
            )
        }
    }
    }
}

/**
 * The battery as a line across the top edge: [level] of the width filled, white.
 *
 * A travelling highlight runs along the filled part while [charging]. Slow, and only over what is
 * already filled — a glint that ran the whole width would read as a progress bar for something,
 * and the one thing a battery line must not look like is a download.
 */
@Composable
private fun BatteryLine(level: Int, charging: Boolean, modifier: Modifier = Modifier) {
    val fill = (level / 100f).coerceIn(0f, 1f)
    val travel by rememberInfiniteTransition(label = "charge").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "travel",
    )
    val low = level <= 20 && !charging
    Box(
        modifier
            .fillMaxWidth()
            .height(BatteryLineHeight)
            .background(Color.White.copy(alpha = 0.10f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fill)
                .height(BatteryLineHeight)
                .drawWithCache {
                    val base = if (low) LowBatteryTint else Color.White
                    val brush = if (!charging) {
                        SolidColor(base)
                    } else {
                        val glint = size.width * 0.22f
                        val head = travel * (size.width + glint * 2f) - glint
                        Brush.linearGradient(
                            colors = listOf(base.copy(alpha = 0.55f), Color.White, base.copy(alpha = 0.55f)),
                            start = Offset(head, 0f),
                            end = Offset(head + glint, 0f),
                        )
                    }
                    onDrawBehind { drawRect(brush) }
                },
        )
    }
}

/** One centre hint: the buttons, quietly, in the band that is chrome rather than content. */
@Composable
private fun StripHint(text: String) {
    Text(text, color = StripMuted, fontSize = LiveDetailSize, fontWeight = FontWeight.Medium)
}

private val LiveArtSize = 26.dp
private val LiveArtCorner = 5.dp
private val LiveTextMax = 220.dp
private val LiveDetailSize = 8.5.sp
/** One hairline, across the top edge of the screen. */
private val BatteryLineHeight = 2.dp

// ── Signal-strength meters (theme-neutral white, level-aware) ──────────────────
//
// Both draw [level] (0..4) as filled vs dimmed segments so the strength reads at a glance. Drawn on
// Canvas rather than shipping five drawables each, and tinted from the strip palette so they sit
// with the rest of the bar.

private val MeterActive   = StripPrimary
private val MeterInactive = Color(0x40EEEEEE)

// Four ascending vertical bars — the classic cellular meter.
@Composable
private fun SignalBars(level: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val bars = 4
        val gap = size.width * 0.14f
        val barWidth = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            val barHeight = size.height * (0.35f + 0.65f * (i + 1) / bars)
            val x = i * (barWidth + gap)
            val top = size.height - barHeight
            drawRect(
                color = if (i < level) MeterActive else MeterInactive,
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

// Wi-Fi "fan": a base dot plus three nested arcs; segments above [level] are dimmed. Level maps as
// dot = 1, +arc = 2, ++arc = 3, +++arc = 4 (0 = all dimmed).
@Composable
private fun WifiMeter(level: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.92f
        val maxR = size.height * 0.9f
        val stroke = size.height * 0.11f

        fun color(threshold: Int) = if (level >= threshold) MeterActive else MeterInactive

        // Base dot (level ≥ 1).
        drawCircle(color = color(1), radius = stroke * 1.1f, center = Offset(cx, cy))
        // Three arcs sweeping upward, growing outward (levels 2, 3, 4).
        for (i in 1..3) {
            val r = maxR * i / 3f
            drawArc(
                color = color(i + 1),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = Stroke(width = stroke),
            )
        }
    }
}

@Composable
private fun StatusIcon(
    @DrawableRes res: Int,
    description: String,
    modifier: Modifier = Modifier,
    tint: Color = StripMuted,
    // Themeable slot: a theme's custom status icon renders as-authored (untinted), like
    // every other icon slot. Null = not themeable (meters drawn on Canvas have no slot).
    slotKey: String? = null,
) {
    val override = slotKey?.let { key ->
        com.psplauncher.core.ui.icons.LocalCustomIcons.current[key]
            ?: com.psplauncher.core.ui.icons.LocalXmbIconOverrides.current[key]
    }
    if (override != null) {
        com.psplauncher.core.ui.icons.CustomIconSurface(
            icon = override,
            contentDescription = description,
            modifier = modifier,
        )
        return
    }
    Image(
        painter            = painterResource(res),
        contentDescription = description,
        colorFilter        = ColorFilter.tint(tint),
        modifier           = modifier,
    )
}

@Composable
private fun StripSeparator() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(10.dp)
            .background(StripSep),
    )
}

/**
 * The strip's height, and therefore the top edge of everything that has to sit under it. Internal
 * rather than private because the panel strip is placed directly beneath it, and a second copy of
 * 28 in the shell is a gap that drifts the first time this changes.
 */
/**
 * Sized to what the content needs on THIS panel: a 26dp art tile and two lines on the left, the
 * clock on the right, and the battery hairline across the top. It was 18dp when the strip was one
 * row of text, and 28dp before the owner asked for "a little bigger".
 *
 * The number itself is core-ui's [StatusStripHeight], not a copy of it. The strip is drawn by the
 * shell over the App Drawer, Settings, Search and the detail pages, and each of those pads itself
 * down by the same band — in four modules that cannot see this one.
 */
internal val StripHeight   = StatusStripHeight

/** The strip's own wash. The hint bar's [BarScrim] value, so the two bands match. */
private val StripScrim = Color(0xB3060200)
internal val StripFontSize = 10.sp

/**
 * Every status icon is exactly the font's height. They were 13dp beside 8sp text, which is an icon
 * set half again as big as the words next to it; "make the icons the same size with the font" is
 * one number, and the widths below are each icon's own aspect against it.
 */
private val StripIconSize  = 10.dp
private val LowBatteryTint = Color(0xFFFF6B6B)

// ── Helpers ───────────────────────────────────────────────────────────────────

// The device's own clock settings, not ours.
//
// These were SimpleDateFormat("h:mm a") and ("MM/dd/yyyy"). Locale.getDefault() was passed, which
// looks like it localises them, but the PATTERN is fixed -- so a device set to 24-hour time, or
// anywhere that does not write dates month-first, was overruled by the launcher. Android exposes
// the user's actual choice for both, including the 24-hour toggle in system settings.
private fun currentTimeString(context: Context): String =
    android.text.format.DateFormat.getTimeFormat(context).format(Date())

private fun currentDateString(context: Context): String =
    android.text.format.DateFormat.getDateFormat(context).format(Date())
