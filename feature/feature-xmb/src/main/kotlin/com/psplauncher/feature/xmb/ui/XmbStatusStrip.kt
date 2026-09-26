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

private val StripPrimary = Color(0xFFEEEEEE)
private val StripMuted   = Color(0xAAEEEEEE)
private val StripSep     = Color(0x55FFFFFF)

object XmbStatusIcons {
    @DrawableRes val bluetooth: Int = R.drawable.ic_status_bluetooth

    @DrawableRes fun battery(level: Int, charging: Boolean): Int =
        requireNotNull(forSlotKey(batterySlotKey(level, charging))) {
            "batterySlotKey produced a key outside the status strip"
        }

    fun batterySlotKey(level: Int, charging: Boolean): String = when {
        charging       -> "status_battery_charging"
        level >= 76    -> "status_battery_full"
        level >= 51    -> "status_battery_high"
        level >= 26    -> "status_battery_medium"
        else           -> "status_battery_low"
    }

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

data class StripLiveActivity(val art: Any?, val title: String, val detail: String?)

data class StripHints(val shoulder: Boolean = false, val leftRight: Boolean = false)

@Composable
fun XmbPspStatusStrip(
    sortLabel: String? = null,

    showSortButton: Boolean = false,
    onSortTapped: () -> Unit = {},

    live: StripLiveActivity? = null,

    hints: StripHints = StripHints(),

    onLiveAreaTapped: (() -> Unit)? = null,
    modifier: Modifier = Modifier,

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

            delay(60_000L - (System.currentTimeMillis() % 60_000L))
        }
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(StripHeight)
            .background(Brush.verticalGradient(0f to StripScrim, 1f to Color.Transparent)),
    ) {
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

        when {
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

@Composable
private fun StripHint(text: String) {
    Text(text, color = StripMuted, fontSize = LiveDetailSize, fontWeight = FontWeight.Medium)
}

private val LiveArtSize = 26.dp
private val LiveArtCorner = 5.dp
private val LiveTextMax = 220.dp
private val LiveDetailSize = 8.5.sp

private val BatteryLineHeight = 2.dp

private val MeterActive   = StripPrimary
private val MeterInactive = Color(0x40EEEEEE)

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

@Composable
private fun WifiMeter(level: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height * 0.92f
        val maxR = size.height * 0.9f
        val stroke = size.height * 0.11f

        fun color(threshold: Int) = if (level >= threshold) MeterActive else MeterInactive

        drawCircle(color = color(1), radius = stroke * 1.1f, center = Offset(cx, cy))

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

internal val StripHeight   = StatusStripHeight

private val StripScrim = Color(0xB3060200)
internal val StripFontSize = 10.sp

private val StripIconSize  = 10.dp
private val LowBatteryTint = Color(0xFFFF6B6B)

private fun currentTimeString(context: Context): String =
    android.text.format.DateFormat.getTimeFormat(context).format(Date())

private fun currentDateString(context: Context): String =
    android.text.format.DateFormat.getDateFormat(context).format(Date())
