package com.psplauncher.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.feature.settings.viewmodel.AudioSettingsViewModel
import com.psplauncher.feature.settings.viewmodel.NO_SOUND_LABEL
import com.psplauncher.themekit.UiMediaLimits

private val AUDIO_PICKER_MIME = UiMediaLimits.AUDIO_MIME.toTypedArray()

@Composable
fun AudioSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AudioSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var focusTargetSlot by remember { mutableStateOf<UiMediaSlot?>(null) }
    var focusRequestToken by remember { mutableIntStateOf(0) }
    var importWasActive by remember { mutableStateOf(false) }

    fun requestSoundFocus(slot: UiMediaSlot) {
        focusTargetSlot = slot
        focusRequestToken++
    }

    val soundPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onSoundPicked(uri)
        } else {
            focusTargetSlot?.let(::requestSoundFocus)
        }
    }

    fun pickFor(slot: UiMediaSlot) {
        requestSoundFocus(slot)
        viewModel.onPickerLaunchedFor(slot)
        soundPicker.launch(AUDIO_PICKER_MIME)
    }

    LaunchedEffect(state.importing) {
        if (state.importing) {
            importWasActive = true
        } else if (importWasActive) {
            importWasActive = false
            focusTargetSlot?.let(::requestSoundFocus)
        }
    }

    var focusedSlot by remember { mutableStateOf<UiMediaSlot?>(null) }

    Box(modifier = modifier) {
        SettingsPageScaffold(
            subtitle = "Sound",
            onBack = onBack,
            helperFooterItems = focusedSlot?.let { slot ->
                MediaRowShortcuts.promptsFor(state.xyLayout, isAssigned = slot in state.assignedSlots)
            } ?: emptyList(),
            onInterceptAction = { action ->
                val slot = focusedSlot ?: return@SettingsPageScaffold false
                when {
                    MediaRowShortcuts.isNorthFace(action, state.xyLayout) &&
                        slot in state.assignedSlots -> {
                        requestSoundFocus(slot)
                        viewModel.useDefault(slot)
                        true
                    }
                    MediaRowShortcuts.isWestFace(action, state.xyLayout) -> {
                        viewModel.preview(slot)
                        true
                    }
                    else -> false
                }
            },
        ) {
            val focusRegistry = LocalSettingsFocusRegistry.current
            LaunchedEffect(focusRequestToken) {
                if (focusRequestToken > 0) {
                    withFrameNanos { }
                    withFrameNanos { }
                    focusTargetSlot?.let { slot ->
                        runCatching { focusRegistry["audio_${slot.key}"]?.requestFocus() }
                    }
                }
            }

            val scrollState = rememberScrollState()
            LocalSettingsScrollStateRegistrar.current(scrollState)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                SettingsGroup("Menu Sounds")

                SettingsToggleRow(
                    label = "Menu Sounds",
                    sublabel = "Play navigation, select, and launch sound effects",
                    checked = state.menuSoundEnabled,
                    onFocusChangedExternal = {
                        if (it) focusedSlot = null
                    },
                    onToggle = { viewModel.setMenuSoundEnabled(it) },
                )

                SettingsGroup("Menu Music")

                SettingsToggleRow(
                    label = "Menu Music",
                    sublabel = "Loop a track of your own while the launcher is on screen.  It " +
                        "stops the moment anything else plays audio — a game, a video, Spotify, " +
                        "a call — and comes back when they are done.  Nothing plays until you " +
                        "pick a track below.",
                    checked = state.menuMusicEnabled,
                    onFocusChangedExternal = { if (it) focusedSlot = null },
                    onToggle = { viewModel.setMenuMusicEnabled(it) },
                )

                MediaAssignmentRow(
                    label = UiMediaSlot.MENU_MUSIC.displayName,
                    focusKey = "audio_${UiMediaSlot.MENU_MUSIC.key}",
                    sublabel = "Up to ${UiMediaLimits.MENU_MUSIC.hardMaxMs / 60_000} minutes, " +
                        "looped (MP3, WAV, OGG or M4A)",
                    value = state.menuMusicLabel,
                    isAssigned = state.menuMusicAssigned,
                    onPick = { pickFor(UiMediaSlot.MENU_MUSIC) },

                    onUseDefault = { viewModel.useDefault(UiMediaSlot.MENU_MUSIC) },
                    resetLabel = "Remove the menu music track",
                    onFocusChanged = { focused ->
                        focusedSlot = if (focused) UiMediaSlot.MENU_MUSIC else null
                    },
                )

                SettingsGroup("Sound Assignments")

                if (state.importing) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 48.dp, vertical = 8.dp),
                    )
                }
                AudioSettingsViewModel.SOUND_SLOTS.forEach { slot ->
                    val label = state.soundLabels[slot] ?: NO_SOUND_LABEL
                    MediaAssignmentRow(
                        label = slot.displayName,
                        focusKey = "audio_${slot.key}",
                        value = label,
                        isAssigned = slot in state.assignedSlots,
                        onPick = { pickFor(slot) },
                        onPreview = { viewModel.preview(slot) },

                        resetLabel = if (slot == UiMediaSlot.LAUNCH_DISC_AUDIO) {
                            "Open the launch disc in silence again"
                        } else {
                            "Use the PSP default for ${slot.displayName}"
                        },
                        onUseDefault = {
                            requestSoundFocus(slot)
                            viewModel.useDefault(slot)
                        },
                        onFocusChanged = { focused ->
                            focusedSlot = if (focused) slot else null
                        },
                    )
                }

                SettingsGroup("")

                SettingsRow(
                    label = "Reset Sound to Defaults",
                    sublabel = "Return every menu, boot and launch sound to the bundled PSP sample and turn Menu Sounds back on",
                    onFocusChangedExternal = { if (it) focusedSlot = null },
                    onClick = { viewModel.requestReset() },
                )
            }
        }
    }

    state.message?.let { message ->
        SettingsMessageOverlay(
            title = "Couldn't use that sound",
            message = message,
            onDismiss = viewModel::dismissMessage,
        )
    }

    if (state.confirmResetVisible) {
        SettingsConfirmOverlay(
            title = "Reset Sound to Defaults?",
            message = "Every menu and boot sound returns to the bundled PSP sample and Menu " +
                "Sounds is turned back on. Your Boot Video and GameBoot media are not affected.",
            confirmLabel = "Reset",
            onConfirm = viewModel::confirmReset,
            onCancel = viewModel::dismissReset,
        )
    }
}
