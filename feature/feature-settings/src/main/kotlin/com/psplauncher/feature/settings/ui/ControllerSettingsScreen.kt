package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.psplauncher.core.domain.model.ConfirmBackLayout
import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.XYLayout
import com.psplauncher.core.domain.model.ScrollSpeed
import com.psplauncher.core.domain.model.StickSensitivity
import com.psplauncher.core.domain.model.displayLabel
import com.psplauncher.feature.settings.viewmodel.ControllerSettingsViewModel

@Composable
fun ControllerSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ControllerSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    SettingsScaffold(
        title    = "Settings",
        subtitle = "Controller",
        onBack   = onBack,
        modifier = modifier,
    ) {
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            SettingsGroup("A / B Swap")
            Text(
                text     = "Controls which button confirms and which goes back. " +
                    "Applies globally to all launcher menus.",
                color    = SettingsSubtext,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
            )

            SettingsPickerRow(
                label    = "A / B Swap",
                sublabel = state.layoutPrefs.confirmBackLayout.displayLabel(),
                options  = ConfirmBackLayout.entries.map {
                    SettingsPickerOption(
                        label = if (it == ConfirmBackLayout.STANDARD) "Off" else "On",
                        help  = it.displayLabel(),
                    )
                },
                selectedIndex = ConfirmBackLayout.entries.indexOf(state.layoutPrefs.confirmBackLayout),
                onPick   = { viewModel.setConfirmBackLayout(ConfirmBackLayout.entries[it]) },
            )

            SettingsGroup("X / Y Swap")
            Text(
                text     = "Swaps X and Y actions within the launcher UI only. " +
                    "Does not affect emulator controls.",
                color    = SettingsSubtext,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
            )

            SettingsPickerRow(
                label    = "X / Y Swap",
                sublabel = state.layoutPrefs.xyLayout.displayLabel(),
                options  = XYLayout.entries.map {
                    SettingsPickerOption(
                        label = if (it == XYLayout.STANDARD) "Off" else "On",
                        help  = it.displayLabel(),
                    )
                },
                selectedIndex = XYLayout.entries.indexOf(state.layoutPrefs.xyLayout),
                onPick   = { viewModel.setXYLayout(XYLayout.entries[it]) },
            )

            SettingsGroup("Controller Type")
            Text(
                text     = "Changes which button icons and labels are shown in help prompts.",
                color    = SettingsSubtext,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
            )

            SettingsPickerRow(
                label    = "Type",
                sublabel = "Affects the help bar at the bottom of the launcher",
                options  = ControllerDisplayType.entries.map { SettingsPickerOption(it.displayLabel()) },
                selectedIndex = ControllerDisplayType.entries.indexOf(state.layoutPrefs.displayType),
                onPick   = { viewModel.setDisplayType(ControllerDisplayType.entries[it]) },
            )

            SettingsGroup("Stick")
            Text(
                text     = "How far the stick must move before it navigates, and how far before " +
                    "it counts as a full tilt. Lower is more deliberate.",
                color    = SettingsSubtext,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
            )

            SettingsPickerRow(
                label    = "Stick Sensitivity",
                sublabel = "Low needs a firmer push and reaches top speed later",
                options  = StickSensitivity.entries.map { SettingsPickerOption(it.displayLabel()) },
                selectedIndex = StickSensitivity.entries.indexOf(state.layoutPrefs.stickSensitivity),
                onPick   = { viewModel.setStickSensitivity(StickSensitivity.entries[it]) },
            )

            SettingsGroup("Scroll Speed")
            Text(
                text     = "How fast lists scroll while a direction is held. Holding longer " +
                    "accelerates, and a full stick tilt accelerates twice as fast.",
                color    = SettingsSubtext,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
            )

            SettingsPickerRow(
                label    = "Scroll Speed",
                sublabel = "Applies to held D-pad and stick navigation",
                options  = ScrollSpeed.entries.map { SettingsPickerOption(it.displayLabel()) },
                selectedIndex = ScrollSpeed.entries.indexOf(state.layoutPrefs.scrollSpeed),
                onPick   = { viewModel.setScrollSpeed(ScrollSpeed.entries[it]) },
            )

            SettingsGroup("Navigation")
            SettingsToggleRow(
                label    = "Left Backs Out",
                sublabel = "Press LEFT to leave a folder, flyout or settings screen — " +
                    "only where LEFT does nothing else",
                checked  = state.layoutPrefs.leftBacksOut,
                onToggle = { viewModel.setLeftBacksOut(it) },
            )

            SettingsGroup("Reset")
            SettingsRow(
                label    = "Reset All Controller Settings",
                sublabel = "Restores default swap and type presets",
                onClick  = { viewModel.resetToDefaults() },
            )
        }
    }
}
