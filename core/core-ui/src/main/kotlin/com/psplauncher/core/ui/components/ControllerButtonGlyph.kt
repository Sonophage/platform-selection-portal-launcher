package com.psplauncher.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.ui.R

// ── Shared controller button glyph ───────────────────────────────────────────
//
// Single source of truth for every location that displays a controller button:
// the wizard footer, the App Drawer command bar, the XMB idle context-menu
// hint, and any future help-prompt row. The icon is chosen by the user's
// controller display family (Settings ▸ Controller ▸ Type), which flows
// reactively from ControllerLayoutRepository.prefs through
// XMBUiState.controllerDisplayType.
//
// Callers request a *physical position* (ControllerIcon), never a printed
// letter. A letter is not a stable key: "B" is the east face button on Xbox and
// the south face button on a Switch. Position is the only family-agnostic
// identity, so the resolver owns the per-family art and UI code stays
// controller-agnostic.
//
// Art provenance (see Controller_Helper_Icon_Mapping.md):
//   PLAYSTATION → PS5 pack,          Buttons Solid/White/128w (DualSense)
//   XBOX        → Xbox Series pack,  Buttons Solid/White/128w
//   NINTENDO    → Switch 2 pack,     Buttons Solid/White/128w, Pro D-Pad art
//
// All three are the same treatment at the same size, so a prompt row reads as one set. The
// PlayStation glyphs were previously the PS4 Premium pack — 480px and a different look, which
// stood out beside the flat white Xbox/Switch art.
//
// Nintendo has its own art for every core input, so no family ever borrows
// another family's letters — their physical A/B and X/Y positions are reversed.

/**
 * The drawable for [family]'s art of this position, or `null` when the input
 * does not exist on that hardware (a touchpad on an Xbox pad, GameChat on a
 * DualSense). Pure, so the whole mapping is unit-testable without Compose.
 *
 * Returning `null` rather than throwing matters: a prompt row that happens to
 * reference a family-exclusive input must degrade, not crash the screen.
 */
@DrawableRes
fun ControllerIcon.drawableForOrNull(family: ControllerDisplayType): Int? =
    when (family) {
        ControllerDisplayType.PLAYSTATION -> psTable
        ControllerDisplayType.NINTENDO -> nsTable
        ControllerDisplayType.XBOX -> xbTable
        // No art, by design. Both render through the printed-label fallback below — the same path
        // a DualSense touchpad takes on an Xbox pad — so neither costs an icon pack.
        ControllerDisplayType.KEYBOARD, ControllerDisplayType.TOUCH -> emptyMap()
    }[this]

/**
 * The label [family] silkscreens on this position ("A", "○", "ZL"), used as the
 * text fallback when no art exists. `null` when the hardware has no such input.
 */
fun ControllerIcon.printedLabelFor(family: ControllerDisplayType): String? =
    when (family) {
        ControllerDisplayType.PLAYSTATION -> psLabels
        ControllerDisplayType.NINTENDO -> nsLabels
        ControllerDisplayType.XBOX -> xbLabels
        ControllerDisplayType.KEYBOARD -> kbLabels
        ControllerDisplayType.TOUCH -> touchLabels
    }[this]

/**
 * How much of the glyph's box the button itself occupies.
 *
 * Measured off the art rather than guessed: the ring's outer edge spans y 22..105 on a 128px
 * canvas. The fill is drawn to exactly that, so the white ring sits on the colour's edge instead
 * of floating inside it or spilling past it.
 */
private const val GlyphDiscFraction = 0.656f

/**
 * The colour a family FILLS a face button with, or null where it prints none.
 *
 * Xbox and PlayStation both colour their four face buttons and it is how people find them — green
 * is where confirm lives on an Xbox pad whatever letter is on it.
 *
 * THESE HEX VALUES ARE NOT SOURCED FROM EITHER MANUFACTURER. They are the conventional colours as
 * commonly drawn, picked to read against a dark bar. Nobody publishes an official sRGB value for
 * a moulded plastic button, and neither pack on disk carries one — the art here is white outlines.
 * If exact values are wanted they have to come from a reference someone names, and this is the one
 * place to change them.
 *
 * Nintendo is absent on purpose. A Switch pad's face buttons are unlabelled grey; colouring them
 * would be inventing a convention rather than following one.
 *
 * Face positions only. A coloured bumper or d-pad is not a thing either pad does, and tinting the
 * whole set would make a prompt row read as decoration instead of as hardware.
 */
fun ControllerIcon.faceFillFor(family: ControllerDisplayType): Color? = when (family) {
    ControllerDisplayType.XBOX -> xbFaceFills
    ControllerDisplayType.PLAYSTATION -> psFaceFills
    else -> emptyMap()
}[this]

// Xbox: A green, B red, X blue, Y yellow — by POSITION, so an X/Y swap moves the art and the
// colour together, because both are resolved from the same position the caller asked for.
private val xbFaceFills = mapOf(
    ControllerIcon.FACE_SOUTH to Color(0xFF6CC24A),
    ControllerIcon.FACE_EAST to Color(0xFFEF4A4A),
    ControllerIcon.FACE_WEST to Color(0xFF4A90D9),
    ControllerIcon.FACE_NORTH to Color(0xFFF2C744),
)

// PlayStation: cross blue, circle red, square pink, triangle green. The DualSense itself prints
// them white; these are the colours the symbols have meant since the first PlayStation, and they
// are what someone scanning a footer is looking for.
private val psFaceFills = mapOf(
    ControllerIcon.FACE_SOUTH to Color(0xFF7FA9E8),
    ControllerIcon.FACE_EAST to Color(0xFFE8767D),
    ControllerIcon.FACE_WEST to Color(0xFFE693D2),
    ControllerIcon.FACE_NORTH to Color(0xFF74D094),
)

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
    // SELECT and SHARE stay distinct concepts even though the DualSense prints
    // one Create button that serves both roles — the art repeats, the IDs do not.
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

// ── Printed labels (text fallback + TalkBack) ────────────────────────────────

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

/**
 * Keys, for a machine with no pad.
 *
 * The face positions take the two keys everything on a desktop already means by them — Enter
 * confirms and Escape backs out — and the D-pad takes the arrows. The shoulders are the keys
 * beside the hand that would be on the arrows, which is a guess, but a named one: nothing prints
 * a standard for "the bumper key".
 */
private val kbLabels = mapOf(
    ControllerIcon.FACE_SOUTH to "Enter", ControllerIcon.FACE_EAST to "Esc",
    ControllerIcon.FACE_WEST to "Shift", ControllerIcon.FACE_NORTH to "Space",
    ControllerIcon.DPAD_UP to "\u2191", ControllerIcon.DPAD_DOWN to "\u2193",
    ControllerIcon.DPAD_LEFT to "\u2190", ControllerIcon.DPAD_RIGHT to "\u2192",
    ControllerIcon.DPAD_ALL to "\u2190\u2192",
    ControllerIcon.BUMPER_LEFT to "Q", ControllerIcon.BUMPER_RIGHT to "E",
    ControllerIcon.TRIGGER_LEFT to "Z", ControllerIcon.TRIGGER_RIGHT to "C",
    ControllerIcon.START to "F1", ControllerIcon.SELECT to "Tab",
    ControllerIcon.SYSTEM to "Home",
)

/**
 * Gestures, for a finger.
 *
 * It names what to DO rather than what to press, which is the only honest prompt for someone
 * holding no hardware. The directions are the swipe that moves that way; the shoulders have no
 * gesture at all and are absent rather than invented, which the resolver already handles by
 * drawing nothing.
 */
private val touchLabels = mapOf(
    ControllerIcon.FACE_SOUTH to "Tap", ControllerIcon.FACE_EAST to "Back",
    ControllerIcon.FACE_NORTH to "Hold",
    ControllerIcon.DPAD_UP to "Swipe up", ControllerIcon.DPAD_DOWN to "Swipe down",
    ControllerIcon.DPAD_LEFT to "Swipe left", ControllerIcon.DPAD_RIGHT to "Swipe right",
    ControllerIcon.DPAD_ALL to "Swipe",
)

private val nsLabels = mapOf(
    // Nintendo's A/B and X/Y are mirrored from Xbox — south is B, east is A.
    ControllerIcon.FACE_SOUTH to "B", ControllerIcon.FACE_EAST to "A",
    ControllerIcon.FACE_WEST to "Y", ControllerIcon.FACE_NORTH to "X",
    ControllerIcon.BUMPER_LEFT to "L", ControllerIcon.BUMPER_RIGHT to "R",
    ControllerIcon.TRIGGER_LEFT to "ZL", ControllerIcon.TRIGGER_RIGHT to "ZR",
    // Nintendo prints no L3/R3 marking; name the press for the text fallback.
    ControllerIcon.STICK_LEFT_CLICK to "L Stick", ControllerIcon.STICK_RIGHT_CLICK to "R Stick",
    ControllerIcon.START to "Plus", ControllerIcon.SELECT to "Minus",
    ControllerIcon.SYSTEM to "Home", ControllerIcon.SHARE to "Capture",
    ControllerIcon.GAME_CHAT to "C", ControllerIcon.CAMERA to "Camera",
    ControllerIcon.PADDLE_LEFT to "GL", ControllerIcon.PADDLE_RIGHT to "GR",
    ControllerIcon.JOYCON_SL to "SL", ControllerIcon.JOYCON_SR to "SR",
)

// ── Rendering ────────────────────────────────────────────────────────────────

/**
 * Renders the [family] art for a physical controller position.
 *
 * Falls back per the icon-mapping contract: art, else the family's printed
 * label as text, else nothing. Glyphs are normalized to [size]: every family's
 * art is 128px today, but sizing here is what guarantees a row stays aligned if
 * a future pack ships at another resolution — as the 480px PlayStation set that
 * these replaced did.
 *
 * The glyph is decorative: callers pair it with an action label that carries
 * the meaning, so semantics are cleared here to avoid a doubled announcement.
 */
@Composable
fun ControllerIconGlyph(
    icon: ControllerIcon,
    family: ControllerDisplayType,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val drawable = icon.drawableForOrNull(family)
    if (drawable != null) {
        val fill = icon.faceFillFor(family)
        if (fill == null) {
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = modifier.size(size),
            )
            return
        }
        // The CIRCLE takes the colour and the symbol stays white — which is how both pads print
        // them. Tinting the art instead coloured the ring AND the letter inside it, so a green A
        // was a green letter on nothing rather than a green button.
        //
        // The art is an outline on a transparent canvas, so the fill is drawn behind it at the
        // ring's own diameter: the white ring lands on the colour's edge and reads as the rim.
        Box(modifier.size(size), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(size * GlyphDiscFraction)
                    .clip(CircleShape)
                    .background(fill),
            )
            Image(
                painter = painterResource(drawable),
                contentDescription = null,
                modifier = Modifier.size(size),
            )
        }
        return
    }
    val label = icon.printedLabelFor(family) ?: return
    Text(
        text = label,
        style = LocalTextStyle.current,
        modifier = modifier.clearAndSetSemantics { },
    )
}
