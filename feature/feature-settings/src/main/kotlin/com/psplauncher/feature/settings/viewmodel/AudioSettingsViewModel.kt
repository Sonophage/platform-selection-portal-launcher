package com.psplauncher.feature.settings.viewmodel

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.datastore.pfpDataStore
import com.psplauncher.core.data.repository.ControllerLayoutRepository
import com.psplauncher.core.data.repository.UiMediaStore
import com.psplauncher.core.domain.model.UiMediaKind
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.core.domain.model.XYLayout
import com.psplauncher.core.ui.media.UiMediaAudioPlayer
import com.psplauncher.core.ui.media.bundledDefaultRes
import com.psplauncher.core.ui.sound.MenuSound
import com.psplauncher.core.ui.sound.MenuSoundPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val KEY_MENU_SOUND = booleanPreferencesKey("sound_menu_enabled")

const val NO_SOUND_LABEL = "None"

data class AudioSettingsUiState(
    val menuSoundEnabled: Boolean = true,
    val menuMusicEnabled: Boolean = false,

    val menuMusicAssigned: Boolean = false,
    val menuMusicLabel: String = NO_SOUND_LABEL,

    val soundLabels: Map<UiMediaSlot, String> = emptyMap(),

    val assignedSlots: Set<UiMediaSlot> = emptySet(),
    val message: String? = null,
    val importing: Boolean = false,
    val confirmResetVisible: Boolean = false,

    val xyLayout: XYLayout = XYLayout.STANDARD,
)

@HiltViewModel
class AudioSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: UiMediaStore,
    private val menuSound: MenuSoundPlayer,
    private val menuMusicPreferences: com.psplauncher.core.data.media.MenuMusicPreferences,
    private val bootSoundPreviewer: UiMediaAudioPlayer,
    private val controllerLayout: ControllerLayoutRepository,
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    private val _importing = MutableStateFlow(false)
    private val _confirmResetVisible = MutableStateFlow(false)

    private var pendingSlot: UiMediaSlot? = null

    val uiState: StateFlow<AudioSettingsUiState> = combine(
        context.pfpDataStore.data,
        controllerLayout.prefs,
        _message,
        _importing,
        _confirmResetVisible,
    ) { prefs, layout, message, importing, confirmReset ->

        val assigned = store.assignments().keys
        val labels = SOUND_SLOTS.associateWith { slot ->
            when {
                slot in assigned -> prefs[UiMediaStore.displayNameKey(slot)] ?: "Custom sound"
                slot.bundledDefaultRes() != null -> UI_MEDIA_DEFAULT_LABEL
                else -> NO_SOUND_LABEL
            }
        }
        AudioSettingsUiState(
            menuSoundEnabled = prefs[KEY_MENU_SOUND] ?: true,
            menuMusicEnabled = com.psplauncher.core.data.media.MenuMusicPreferences.resolve(prefs),
            menuMusicAssigned = UiMediaSlot.MENU_MUSIC in assigned,
            menuMusicLabel = if (UiMediaSlot.MENU_MUSIC in assigned) {
                prefs[UiMediaStore.displayNameKey(UiMediaSlot.MENU_MUSIC)] ?: "Custom track"
            } else {
                "None — pick a track"
            },
            soundLabels = labels,

            assignedSlots = assigned.filterTo(HashSet()) { it in SCREEN_SLOTS },
            message = message,
            importing = importing,
            confirmResetVisible = confirmReset,
            xyLayout = layout.xyLayout,
        )
    }

        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioSettingsUiState())

    fun setMenuSoundEnabled(enabled: Boolean) = viewModelScope.launch {
        context.pfpDataStore.edit { it[KEY_MENU_SOUND] = enabled }
    }

    fun setMenuMusicEnabled(enabled: Boolean) = viewModelScope.launch {
        menuMusicPreferences.setEnabled(enabled)
    }

    fun onPickerLaunchedFor(slot: UiMediaSlot) {
        pendingSlot = slot
    }

    fun onSoundPicked(uri: Uri) {
        val slot = pendingSlot ?: return
        pendingSlot = null
        viewModelScope.launch {
            _importing.value = true
            val result = store.import(slot, uri)
            _importing.value = false
            if (result.ok) {
                menuSound.refreshCustomSamples()
            } else {
                menuSound.play(MenuSound.ERROR)
                _message.value = result.message
            }
        }
    }

    fun preview(slot: UiMediaSlot) {
        val event = MenuSound.entries.firstOrNull { it.slot == slot }
        if (event != null) {
            menuSound.play(event, ignoreMute = true)
        } else {
            bootSoundPreviewer.play(slot = slot, customPath = store.pathFor(slot))
        }
    }

    fun useDefault(slot: UiMediaSlot) = viewModelScope.launch {
        store.clear(slot)
    }

    fun requestReset() { _confirmResetVisible.value = true }
    fun dismissReset() { _confirmResetVisible.value = false }

    fun confirmReset() = viewModelScope.launch {
        _confirmResetVisible.value = false
        menuSound.play(MenuSound.CONFIRM)
        store.clearAll(UiMediaKind.SOUND)

        SOUND_SLOTS.filter { it.kind == UiMediaKind.AUDIO_TRACK }.forEach { store.clear(it) }
        context.pfpDataStore.edit { it[KEY_MENU_SOUND] = true }
    }

    fun dismissMessage() { _message.value = null }

    fun stopBootPreview() = bootSoundPreviewer.stop()

    override fun onCleared() {
        stopBootPreview()
    }

    companion object {
        val SOUND_SLOTS: List<UiMediaSlot> =
            UiMediaSlot.ofKind(UiMediaKind.SOUND) +
                UiMediaSlot.BOOT_AUDIO +
                UiMediaSlot.LAUNCH_DISC_AUDIO +
                UiMediaSlot.GAMEBOOT_AUDIO

        private val SCREEN_SLOTS: Set<UiMediaSlot> = SOUND_SLOTS.toSet() + UiMediaSlot.MENU_MUSIC
    }
}
