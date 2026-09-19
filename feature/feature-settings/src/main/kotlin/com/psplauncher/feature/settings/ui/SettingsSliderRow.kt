package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import com.psplauncher.themekit.XmbLayoutSpec
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.round

/**
 * The controller-side handle for a slider currently in adjust mode. The scaffold holds one of
 * these while the user is stepping a slider and forwards LEFT/RIGHT to [onStep]. The row builds
 * the node from its OWN latest value (kept in a state holder, refreshed every recomposition), so
 * an already-stored node can never step from a stale base value.
 */
internal class SettingsSliderNode(
    // Change the slider's value by [delta] discrete steps (-1 / +1). The row owns the mapping
    // from step to value so this type stays decoupled from any specific range/granularity.
    val onStep: (Int) -> Unit,
)

/**
 * A slider rendered as a full controller-navigable row (a "slider node"):
 *
 *  - UP/DOWN traverse onto/off it like any other settings row (it never claims the screen's
 *    initial focus).
 *  - SELECT (A) — or tapping the row — enters adjust mode: LEFT/RIGHT step the value (gamepad
 *    auto-repeat applies for held buttons), and the value text + slider turn accent-coloured.
 *  - BACK (or SELECT again) exits adjust mode back to ordinary row navigation. While adjusting,
 *    BACK is consumed by the scaffold — it can never pop the settings screen.
 *  - The Material slider underneath stays fully touch-draggable at all times (touch also exits
 *    controller adjust mode, like any pointer activity on the screen).
 */
@Composable
fun SettingsSliderRow(
    label: String,
    sublabel: String? = null,
    focusKey: String? = null,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueFormatter: (Float) -> String = { it.toString() },
    enabled: Boolean = true,
) {
    val focusTracker = LocalSettingsFocusTracker.current
    val touchInput = LocalSettingsTouchInput.current
    val cursorVisible = LocalSettingsCursorVisible.current
    val reportFocused = LocalSettingsReportFocused.current
    val help = com.psplauncher.feature.settings.ui.LocalSettingsHelp.current
    val bloom = com.psplauncher.core.ui.theme.LocalPFPColors.current.waveColor
    val enterSliderMode = LocalSettingsEnterSliderMode.current
    val adjusting = LocalSettingsSliderAdjusting.current
    var isFocused by remember { mutableStateOf(false) }

    // Latest-value holder: the adjust node is built at SELECT time but steps must read the value
    // as it is NOW. `latestValue` is refreshed every recomposition via SideEffect, so a stored
    // node keeps stepping from current state regardless of how many steps already applied.
    val latestValue = remember { mutableStateOf(value) }
    SideEffect { latestValue.value = value }
    val stepSize = if (steps > 0) (valueRange.endInclusive - valueRange.start) / (steps + 1) else 0f

    val enterAdjustment = {
        val node = SettingsSliderNode(
            onStep = { delta ->
                val raw = latestValue.value + delta * stepSize
                val next = if (steps > 0) round(raw / stepSize) * stepSize else raw
                onValueChange(next.coerceIn(valueRange.start, valueRange.endInclusive))
            },
        )
        enterSliderMode(node)
    }

    // A normal navigable row: vertical order with every other row, SELECT enters adjust mode.
    // It does NOT claim the screen's initial focus (a screen still opens on its first action row).
    val row = rememberControllerRowRegistration(
        prefix = "slider",
        focusKey = focusKey,
        claimInitialFocus = false,
        selectable = enabled,
        enabled = enabled,
        onSelect = { enterAdjustment() },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(row.focusRequester)
            .then(row.positionReporting)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) {
                    focusTracker(enterAdjustment)
                    reportFocused(row.focusRequester)
                    // Same band as every other row's helper line — see LocalSettingsHelp.
                    help.value = sublabel
                }
            }
            // The same left bloom as every other focused row (see SettingsRow). It was a flat
            // menuCursorFill, which after the rows moved to the bloom left this one row painting
            // a pale slab with a hard right edge in the middle of a list that no longer had one.
            .drawBehind {
                if (isFocused && cursorVisible) {
                    drawRect(
                        Brush.horizontalGradient(
                            0f to bloom.copy(alpha = 0.62f),
                            0.5f to bloom.copy(alpha = 0.14f),
                            1f to Color.Transparent,
                        )
                    )
                }
            }
            .focusable()
            .padding(horizontal = 48.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Selection is scale here too, or this row would be the only one in the list
                // that did not grow under the cursor.
                val rowSelected = isFocused && cursorVisible
                Text(
                    text = label,
                    color = if (rowSelected) Color.White else SettingsText,
                    fontSize = if (rowSelected) XmbLayoutSpec.DEFAULT.itemTextSelectedSp.sp
                               else XmbLayoutSpec.DEFAULT.itemTextSp.sp,
                    fontWeight = if (rowSelected) FontWeight.SemiBold else FontWeight.Normal,
                    style = TextStyle(shadow = SettingsTextShadow),
                )
                // [sublabel] is reported to the help band, not drawn here -- see LocalSettingsHelp.
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = valueFormatter(value),
                color = if (adjusting) SettingsAccent else SettingsSubtext,
                fontSize = 13.sp,
                style = TextStyle(shadow = SettingsTextShadow),
            )
        }
        Spacer(Modifier.height(8.dp))
        Slider(
            value = value,
            onValueChange = { v ->
                // Dragging is touch input — it also ends controller adjust mode via the scaffold's
                // touch handler (pointer activity clears sliderNodeState).
                touchInput()
                onValueChange(v)
            },
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = if (adjusting) SettingsAccent else SettingsSubtext,
                activeTrackColor = if (adjusting) SettingsAccent else SettingsDivider,
                inactiveTrackColor = SettingsDivider.copy(alpha = 0.4f),
            ),
        )
    }
    // No trailing rule. Row dividers were removed from this screen family when selection became
    // scale rather than a bar; this one survived because it is drawn by the slider and not by
    // SettingsRow, and it read as a stray line under whichever slider the cursor was on.
}