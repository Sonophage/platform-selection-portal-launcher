package com.psplauncher.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.ui.R

@DrawableRes
fun ControllerIcon.drawableForOrNull(family: ControllerDisplayType): Int? =
    when (family) {
        ControllerDisplayType.PLAYSTATION -> psTable
        ControllerDisplayType.NINTENDO -> nsTable
        ControllerDisplayType.XBOX -> xbTable

        ControllerDisplayType.KEYBOARD, ControllerDisplayType.TOUCH -> emptyMap()
    }[this]

fun ControllerIcon.printedLabelFor(family: ControllerDisplayType): String? =
    when (family) {
        ControllerDisplayType.PLAYSTATION -> psLabels
        ControllerDisplayType.NINTENDO -> nsLabels
        ControllerDisplayType.XBOX -> xbLabels
        ControllerDisplayType.KEYBOARD -> kbLabels
        ControllerDisplayType.TOUCH -> touchLabels
    }[this]

private val psTable = mapOf(
    ControllerIcon.FACE_SOUTH to R.drawable.ctl_ps_face_south,
    ControllerIcon.FACE_EAST to R.drawable.ctl_ps_face_east,
    ControllerIcon.FACE_WEST to R.drawable.ctl_ps_face_west,
    ControllerIcon.FACE_NORTH to R.drawable.ctl_ps_face_north,
    ControllerIcon.DPAD_UP to R.drawable.ctl_ps_dpad_up,
    ControllerIcon.DPAD_DOWN to R.drawable.ctl_ps_dpad_down,
    ControllerIcon.DPAD_LEFT to R.drawable.ctl_ps_dpad_left,
    ControllerIcon.DPAD_RIGHT to R.drawable.ctl_ps_dpad_right,
    ControllerIcon.DPAD_ALL to R.drawable.ctl_ps_dpad_all,
    ControllerIcon.BUMPER_LEFT to R.drawable.ctl_ps_bumper_left,
    ControllerIcon.BUMPER_RIGHT to R.drawable.ctl_ps_bumper_right,
    ControllerIcon.TRIGGER_LEFT to R.drawable.ctl_ps_trigger_left,
    ControllerIcon.TRIGGER_RIGHT to R.drawable.ctl_ps_trigger_right,
    ControllerIcon.STICK_LEFT to R.drawable.ctl_ps_stick_left,
    ControllerIcon.STICK_RIGHT to R.drawable.ctl_ps_stick_right,
    ControllerIcon.STICK_LEFT_CLICK to R.drawable.ctl_ps_stick_left_click,
    ControllerIcon.STICK_RIGHT_CLICK to R.drawable.ctl_ps_stick_right_click,
    ControllerIcon.START to R.drawable.ctl_ps_start,

    ControllerIcon.SELECT to R.drawable.ctl_ps_select,
    ControllerIcon.SYSTEM to R.drawable.ctl_ps_system,
    ControllerIcon.SHARE to R.drawable.ctl_ps_share,
    ControllerIcon.TOUCHPAD to R.drawable.ctl_ps_touchpad,
    ControllerIcon.TOUCHPAD_LEFT to R.drawable.ctl_ps_touchpad_left,
    ControllerIcon.TOUCHPAD_RIGHT to R.drawable.ctl_ps_touchpad_right,
)

private val xbTable = mapOf(
    ControllerIcon.FACE_SOUTH to R.drawable.ctl_xb_face_south,
    ControllerIcon.FACE_EAST to R.drawable.ctl_xb_face_east,
    ControllerIcon.FACE_WEST to R.drawable.ctl_xb_face_west,
    ControllerIcon.FACE_NORTH to R.drawable.ctl_xb_face_north,
    ControllerIcon.DPAD_UP to R.drawable.ctl_xb_dpad_up,
    ControllerIcon.DPAD_DOWN to R.drawable.ctl_xb_dpad_down,
    ControllerIcon.DPAD_LEFT to R.drawable.ctl_xb_dpad_left,
    ControllerIcon.DPAD_RIGHT to R.drawable.ctl_xb_dpad_right,
    ControllerIcon.DPAD_ALL to R.drawable.ctl_xb_dpad_all,
    ControllerIcon.BUMPER_LEFT to R.drawable.ctl_xb_bumper_left,
    ControllerIcon.BUMPER_RIGHT to R.drawable.ctl_xb_bumper_right,
    ControllerIcon.TRIGGER_LEFT to R.drawable.ctl_xb_trigger_left,
    ControllerIcon.TRIGGER_RIGHT to R.drawable.ctl_xb_trigger_right,
    ControllerIcon.STICK_LEFT to R.drawable.ctl_xb_stick_left,
    ControllerIcon.STICK_RIGHT to R.drawable.ctl_xb_stick_right,
    ControllerIcon.STICK_LEFT_CLICK to R.drawable.ctl_xb_stick_left_click,
    ControllerIcon.STICK_RIGHT_CLICK to R.drawable.ctl_xb_stick_right_click,
    ControllerIcon.START to R.drawable.ctl_xb_start,
    ControllerIcon.SELECT to R.drawable.ctl_xb_select,
    ControllerIcon.SYSTEM to R.drawable.ctl_xb_system,
    ControllerIcon.SHARE to R.drawable.ctl_xb_share,
)

private val nsTable = mapOf(
    ControllerIcon.FACE_SOUTH to R.drawable.ctl_ns_face_south,
    ControllerIcon.FACE_EAST to R.drawable.ctl_ns_face_east,
    ControllerIcon.FACE_WEST to R.drawable.ctl_ns_face_west,
    ControllerIcon.FACE_NORTH to R.drawable.ctl_ns_face_north,
    ControllerIcon.DPAD_UP to R.drawable.ctl_ns_dpad_up,
    ControllerIcon.DPAD_DOWN to R.drawable.ctl_ns_dpad_down,
    ControllerIcon.DPAD_LEFT to R.drawable.ctl_ns_dpad_left,
    ControllerIcon.DPAD_RIGHT to R.drawable.ctl_ns_dpad_right,
    ControllerIcon.DPAD_ALL to R.drawable.ctl_ns_dpad_all,
    ControllerIcon.BUMPER_LEFT to R.drawable.ctl_ns_bumper_left,
    ControllerIcon.BUMPER_RIGHT to R.drawable.ctl_ns_bumper_right,
    ControllerIcon.TRIGGER_LEFT to R.drawable.ctl_ns_trigger_left,
    ControllerIcon.TRIGGER_RIGHT to R.drawable.ctl_ns_trigger_right,
    ControllerIcon.STICK_LEFT to R.drawable.ctl_ns_stick_left,
    ControllerIcon.STICK_RIGHT to R.drawable.ctl_ns_stick_right,
    ControllerIcon.STICK_LEFT_CLICK to R.drawable.ctl_ns_stick_left_click,
    ControllerIcon.STICK_RIGHT_CLICK to R.drawable.ctl_ns_stick_right_click,
    ControllerIcon.START to R.drawable.ctl_ns_start,
    ControllerIcon.SELECT to R.drawable.ctl_ns_select,
    ControllerIcon.SYSTEM to R.drawable.ctl_ns_system,
    ControllerIcon.SHARE to R.drawable.ctl_ns_share,
    ControllerIcon.GAME_CHAT to R.drawable.ctl_ns_game_chat,
    ControllerIcon.CAMERA to R.drawable.ctl_ns_camera,
    ControllerIcon.PADDLE_LEFT to R.drawable.ctl_ns_paddle_left,
    ControllerIcon.PADDLE_RIGHT to R.drawable.ctl_ns_paddle_right,
    ControllerIcon.JOYCON_SL to R.drawable.ctl_ns_joycon_sl,
    ControllerIcon.JOYCON_SR to R.drawable.ctl_ns_joycon_sr,
)

private val psLabels = mapOf(
    ControllerIcon.FACE_SOUTH to "Cross", ControllerIcon.FACE_EAST to "Circle",
    ControllerIcon.FACE_WEST to "Square", ControllerIcon.FACE_NORTH to "Triangle",
    ControllerIcon.BUMPER_LEFT to "L1", ControllerIcon.BUMPER_RIGHT to "R1",
    ControllerIcon.TRIGGER_LEFT to "L2", ControllerIcon.TRIGGER_RIGHT to "R2",
    ControllerIcon.STICK_LEFT_CLICK to "L3", ControllerIcon.STICK_RIGHT_CLICK to "R3",
    ControllerIcon.START to "Options", ControllerIcon.SELECT to "Create",
    ControllerIcon.SYSTEM to "PS", ControllerIcon.SHARE to "Create",
    ControllerIcon.TOUCHPAD to "Touchpad",
    ControllerIcon.TOUCHPAD_LEFT to "Touchpad left",
    ControllerIcon.TOUCHPAD_RIGHT to "Touchpad right",
)

private val xbLabels = mapOf(
    ControllerIcon.FACE_SOUTH to "A", ControllerIcon.FACE_EAST to "B",
    ControllerIcon.FACE_WEST to "X", ControllerIcon.FACE_NORTH to "Y",
    ControllerIcon.BUMPER_LEFT to "LB", ControllerIcon.BUMPER_RIGHT to "RB",
    ControllerIcon.TRIGGER_LEFT to "LT", ControllerIcon.TRIGGER_RIGHT to "RT",
    ControllerIcon.STICK_LEFT_CLICK to "LS", ControllerIcon.STICK_RIGHT_CLICK to "RS",
    ControllerIcon.START to "Menu", ControllerIcon.SELECT to "View",
    ControllerIcon.SYSTEM to "Xbox", ControllerIcon.SHARE to "Share",
)

private val kbLabels = mapOf(
    ControllerIcon.FACE_SOUTH to "Enter", ControllerIcon.FACE_EAST to "Esc",
    ControllerIcon.FACE_WEST to "F2", ControllerIcon.FACE_NORTH to "F3",
    ControllerIcon.DPAD_UP to "\u2191", ControllerIcon.DPAD_DOWN to "\u2193",
    ControllerIcon.DPAD_LEFT to "\u2190", ControllerIcon.DPAD_RIGHT to "\u2192",
    ControllerIcon.DPAD_ALL to "\u2190\u2192",
    ControllerIcon.BUMPER_LEFT to "PgUp", ControllerIcon.BUMPER_RIGHT to "PgDn",
    ControllerIcon.START to "F1", ControllerIcon.SELECT to "Tab",
    ControllerIcon.SYSTEM to "Home",
)

private val touchLabels = mapOf(
    ControllerIcon.FACE_SOUTH to "Tap", ControllerIcon.FACE_EAST to "Back",
    ControllerIcon.FACE_NORTH to "Hold",

    ControllerIcon.DPAD_UP to "\u2191", ControllerIcon.DPAD_DOWN to "\u2193",
    ControllerIcon.DPAD_LEFT to "\u2190", ControllerIcon.DPAD_RIGHT to "\u2192",
    ControllerIcon.DPAD_ALL to "\u2190\u2192",
)

private val nsLabels = mapOf(

    ControllerIcon.FACE_SOUTH to "B", ControllerIcon.FACE_EAST to "A",
    ControllerIcon.FACE_WEST to "Y", ControllerIcon.FACE_NORTH to "X",
    ControllerIcon.BUMPER_LEFT to "L", ControllerIcon.BUMPER_RIGHT to "R",
    ControllerIcon.TRIGGER_LEFT to "ZL", ControllerIcon.TRIGGER_RIGHT to "ZR",

    ControllerIcon.STICK_LEFT_CLICK to "L Stick", ControllerIcon.STICK_RIGHT_CLICK to "R Stick",
    ControllerIcon.START to "Plus", ControllerIcon.SELECT to "Minus",
    ControllerIcon.SYSTEM to "Home", ControllerIcon.SHARE to "Capture",
    ControllerIcon.GAME_CHAT to "C", ControllerIcon.CAMERA to "Camera",
    ControllerIcon.PADDLE_LEFT to "GL", ControllerIcon.PADDLE_RIGHT to "GR",
    ControllerIcon.JOYCON_SL to "SL", ControllerIcon.JOYCON_SR to "SR",
)

@Composable
fun ControllerIconGlyph(
    icon: ControllerIcon,
    family: ControllerDisplayType,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val drawable = icon.drawableForOrNull(family)
    if (drawable != null) {
        Image(
            painter = painterResource(drawable),
            contentDescription = null,
            modifier = modifier.size(size),
        )
        return
    }
    val label = icon.printedLabelFor(family) ?: return

    val capText = with(LocalDensity.current) { (size * KeycapTextRatio).toSp() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(size)
            .widthIn(min = size)
            .clip(RoundedCornerShape(size * KeycapCornerRatio))
            .background(Color.White.copy(alpha = 0.14f))
            .padding(horizontal = size * KeycapPadRatio)
            .clearAndSetSemantics { },
    ) {
        Text(
            text = label,
            fontSize = capText,
            lineHeight = capText,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private const val KeycapCornerRatio = 0.25f

private const val KeycapTextRatio = 0.34f

private const val KeycapPadRatio = 0.22f
