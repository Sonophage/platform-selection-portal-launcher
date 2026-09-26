package com.psplauncher.launcher.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.feature.xmb.preview.PreviewData
import com.psplauncher.feature.xmb.viewmodel.XMBUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugMenuViewModel @Inject constructor(
    private val debugController: DebugController,
    private val debugSeeder: DebugSeeder,
) : ViewModel() {
    val debugState: StateFlow<DebugState> = debugController.state

    val previewState: XMBUiState
        get() = when (debugController.currentState.scenario) {
            DebugScenario.EMPTY_LIBRARY  -> PreviewData.emptyLibraryState
            DebugScenario.MISSING_ROMS   -> PreviewData.defaultState
            DebugScenario.NO_ARTWORK     -> PreviewData.defaultState
            else                         -> PreviewData.defaultState
        }

    fun setUseFakeData(use: Boolean) = debugController.setUseFakeData(use)

    fun setForceWaveMode(mode: ForceWaveMode) = debugController.setForceWaveMode(mode)

    fun setSimulatedThermal(thermal: SimulatedThermal) = debugController.setSimulatedThermal(thermal)

    fun setShowTaskTray(show: Boolean) = debugController.setShowTaskTray(show)

    fun setShowBootOnNextLaunch(show: Boolean) = debugController.setShowBootOnNextLaunch(show)

    fun reset() = debugController.reset()

    fun reseed(scenario: DebugScenario) {
        viewModelScope.launch {
            debugSeeder.reseed(scenario)
        }
    }
}
