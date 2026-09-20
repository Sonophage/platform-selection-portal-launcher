package com.psplauncher.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.repository.ControllerLayoutRepository
import com.psplauncher.core.data.repository.ControllerMappingRepository
import com.psplauncher.core.domain.model.ConfirmBackLayout
import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerLayoutPrefs
import com.psplauncher.core.domain.model.ScrollSpeed
import com.psplauncher.core.domain.model.XYLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ControllerSettingsUiState(
    val layoutPrefs: ControllerLayoutPrefs = ControllerLayoutPrefs(),
)

@HiltViewModel
class ControllerSettingsViewModel @Inject constructor(
    private val mappingRepository: ControllerMappingRepository,
    private val layoutRepository: ControllerLayoutRepository,
) : ViewModel() {

    val uiState: StateFlow<ControllerSettingsUiState> = layoutRepository.prefs
        .map { layoutPrefs ->
            ControllerSettingsUiState(layoutPrefs = layoutPrefs)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ControllerSettingsUiState())

    fun setConfirmBackLayout(layout: ConfirmBackLayout) {
        viewModelScope.launch { layoutRepository.setConfirmBackLayout(layout) }
    }

    fun setXYLayout(layout: XYLayout) {
        viewModelScope.launch { layoutRepository.setXYLayout(layout) }
    }

    fun setScrollSpeed(speed: ScrollSpeed) {
        viewModelScope.launch { layoutRepository.setScrollSpeed(speed) }
    }

    fun setLeftBacksOut(enabled: Boolean) {
        viewModelScope.launch { layoutRepository.setLeftBacksOut(enabled) }
    }

    fun setDisplayType(type: ControllerDisplayType) {
        viewModelScope.launch { layoutRepository.setDisplayType(type) }
    }

    fun resetToDefaults() {
        viewModelScope.launch {
            mappingRepository.resetToDefaults()
            layoutRepository.resetAllPrefs()
        }
    }
}
