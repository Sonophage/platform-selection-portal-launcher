package com.psplauncher.feature.settings.ui

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

internal class SettingsSliderNode(

    val onStep: (Int) -> Unit,
)

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

                    help.value = sublabel
                }
            }

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
                val rowSelected = isFocused && cursorVisible
                Text(
                    text = label,
                    color = if (rowSelected) Color.White else SettingsText,
                    fontSize = if (rowSelected) XmbLayoutSpec.DEFAULT.itemTextSelectedSp.sp
                               else XmbLayoutSpec.DEFAULT.itemTextSp.sp,
                    fontWeight = if (rowSelected) FontWeight.SemiBold else FontWeight.Normal,
                    style = TextStyle(shadow = SettingsTextShadow),
                )
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
}
