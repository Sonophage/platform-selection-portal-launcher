package com.psplauncher.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.psplauncher.core.domain.model.GamepadAction

import com.psplauncher.core.domain.model.SETTINGS_ROOT_SCREEN_ID

val SETTINGS_SCREEN_ROUTES: Set<String> = setOf(
    SETTINGS_ROOT_SCREEN_ID,
    "settings_overview",
    "settings_initial_setup",
    "settings_initial_setup_first",
    "settings_library",
    "settings_import_pc",
    "settings_music",
    "settings_video",
    "settings_photo",
    "settings_books",
    "settings_categories",
    "settings_artwork",
    "settings_artwork_sources",
    "settings_artwork_import",
    "settings_emulators",

    "settings_emulators_installed",
    "settings_emulators_custom",
    "settings_emulators_retroarch",
    "settings_emulators_assign",
    "settings_themes",
    "settings_display",

    "settings_appearance",
    "settings_layout",
    "settings_boot",
    "settings_touch",
    "settings_performance",
    "settings_audio",
    "settings_controller",
    "settings_backup",
    "settings_logs",
    "settings_about",
    "settings_credits",

    "settings_app_visibility",
)

@Composable
fun SettingsNavHost(
    screenId: String,
    onBack: () -> Unit,
    pendingGamepadAction: GamepadAction? = null,
    onGamepadActionConsumed: () -> Unit = {},
    showControllerHint: Boolean = false,

    leftBacksOut: Boolean = true,

    lastInputWasTouch: Boolean = false,
    onTouchInteraction: () -> Unit = {},

    onPromptTapped: ((GamepadAction) -> Unit)? = null,
    onOpenColorSchemePicker: () -> Unit = {},
    onOpenXmbLayoutAdjust: () -> Unit = {},
    onOpenCustomIcons: () -> Unit = {},
    onPreviewBootSequence: () -> Unit = {},
    onPreviewGameBoot: () -> Unit = {},
    onAddAndroidApps: () -> Unit = {},
    onOpenLibraryManager: () -> Unit = {},
    onGoToLibrary: () -> Unit = {},

    onOpenScreen: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val overlayInput = remember(screenId) { mutableStateOf<((GamepadAction) -> Unit)?>(null) }
    CompositionLocalProvider(
        LocalSettingsOverlayInput provides overlayInput,
        LocalSettingsScreenId provides screenId,
        LocalSettingsOpenScreen provides onOpenScreen,
        LocalSettingsPendingAction provides pendingGamepadAction,
        LocalSettingsActionConsumed provides onGamepadActionConsumed,
        LocalSettingsShowControllerHint provides showControllerHint,
        LocalSettingsPromptAction provides onPromptTapped,
        LocalSettingsLeftBacksOut provides leftBacksOut,
        LocalSettingsLastInputWasTouch provides lastInputWasTouch,
        LocalSettingsHostTouchInput provides onTouchInteraction,
    ) {
        when (screenId) {
            "settings_initial_setup" -> InitialSetupScreen(
                onBack = onBack,
                onOpenLibraryManager = onOpenLibraryManager,
                onGoToLibrary = onGoToLibrary,
                modifier = modifier,
            )

            "settings_initial_setup_first" -> InitialSetupScreen(
                onBack = onBack,
                firstRun = true,
                onOpenLibraryManager = onOpenLibraryManager,
                onGoToLibrary = onGoToLibrary,
                modifier = modifier,
            )
            SETTINGS_ROOT_SCREEN_ID -> SettingsRootScreen(
                onOpenSection = onOpenScreen,
                onBack = onBack,
                modifier = modifier,
            )
            "settings_library"    -> LibraryManagerScreen(onBack = onBack, onAddAndroidApps = onAddAndroidApps, modifier = modifier)

            "settings_import_pc"  -> LibraryManagerScreen(onBack = onBack, onAddAndroidApps = onAddAndroidApps, startInImportPc = true, modifier = modifier)
            "settings_music"      -> MusicSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_video"      -> VideoSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_photo"      -> PhotoSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_books"      -> BooksSettingsScreen(onBack = onBack, modifier = modifier)

            "settings_categories" -> CategoryManagerScreen(onBack = onBack, modifier = modifier)
            "settings_artwork"    -> ArtworkSettingsScreen(
                onBack = onBack, section = ArtworkSection.ARTWORK, modifier = modifier,
            )
            "settings_artwork_sources" -> ArtworkSettingsScreen(
                onBack = onBack, section = ArtworkSection.SOURCES, modifier = modifier,
            )
            "settings_artwork_import" -> ArtworkImportScreen(onBack = onBack, modifier = modifier)
            "settings_emulators"  -> EmulatorsSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_emulators_installed" -> EmulatorsSettingsScreen(onBack = onBack, section = EmulatorSettingsSection.INSTALLED, modifier = modifier)
            "settings_emulators_custom" -> EmulatorsSettingsScreen(onBack = onBack, section = EmulatorSettingsSection.CUSTOM, modifier = modifier)
            "settings_emulators_retroarch" -> EmulatorsSettingsScreen(onBack = onBack, section = EmulatorSettingsSection.RETROARCH, modifier = modifier)
            "settings_emulators_assign" -> EmulatorAssignmentScreen(onBack = onBack, modifier = modifier)
            "settings_themes"     -> ThemesSettingsScreen(
                onBack = onBack,
                onOpenColorSchemePicker = onOpenColorSchemePicker,
                modifier = modifier,
            )
            "settings_display"    -> DisplaySettingsScreen(
                onBack = onBack,
                onOpenXmbLayoutAdjust = onOpenXmbLayoutAdjust,
                onOpenCustomIcons = onOpenCustomIcons,
                onPreviewBootSequence = onPreviewBootSequence,
                onPreviewGameBoot = onPreviewGameBoot,
                modifier = modifier,
            )
            "settings_appearance" -> DisplaySettingsScreen(
                onBack = onBack, section = DisplaySection.APPEARANCE, modifier = modifier,
            )
            "settings_layout"     -> DisplaySettingsScreen(
                onBack = onBack, section = DisplaySection.LAYOUT,
                onOpenXmbLayoutAdjust = onOpenXmbLayoutAdjust,
                onOpenCustomIcons = onOpenCustomIcons,
                modifier = modifier,
            )
            "settings_boot"       -> DisplaySettingsScreen(
                onBack = onBack, section = DisplaySection.BOOT,
                onPreviewBootSequence = onPreviewBootSequence,
                onPreviewGameBoot = onPreviewGameBoot,
                modifier = modifier,
            )
            "settings_touch"      -> DisplaySettingsScreen(
                onBack = onBack, section = DisplaySection.INPUT, modifier = modifier,
            )
            "settings_performance" -> DisplaySettingsScreen(
                onBack = onBack, section = DisplaySection.PERFORMANCE, modifier = modifier,
            )
            "settings_audio"      -> AudioSettingsScreen(
                onBack = onBack,
                modifier = modifier,
            )
            "settings_controller" -> ControllerSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_backup"     -> BackupSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_logs"       -> LogsSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_overview"   -> OverviewSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_about"      -> AboutSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_credits"    -> CreditsSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_app_visibility" -> AppVisibilitySettingsScreen(onBack = onBack, modifier = modifier)
        }
    }
}
