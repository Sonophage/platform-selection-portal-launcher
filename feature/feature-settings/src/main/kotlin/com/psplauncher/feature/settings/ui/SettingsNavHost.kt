package com.psplauncher.feature.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.psplauncher.core.domain.model.GamepadAction

// Every screen route SettingsNavHost resolves. Kept beside the `when` (whose branches are string
// literals) so the settings-hierarchy tests can verify that every L2 row id and every legacy
// direct-caller id resolves here. New screens MUST be added to both this set and the `when`.
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
    // Emulators section entry points — distinct ids keep the L2 list keys stable until
    // per-section focus targets land.
    "settings_emulators_installed",
    "settings_emulators_custom",
    "settings_emulators_retroarch",
    "settings_emulators_assign",
    "settings_themes",
    "settings_display",
    // Display's groups, each reachable on its own. The screen is one file; these pick which part
    // of it renders, the same way the emulator rows do.
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
    // Hidden Items manager — moved out of Display; also the Library ▸ Hidden Games target.
    "settings_app_visibility",
)

// Routes a settings item ID to the correct full-screen settings composable.
// Shown as an overlay on top of XMBShell when the user selects a Settings sub-item.
@Composable
fun SettingsNavHost(
    screenId: String,
    onBack: () -> Unit,
    pendingGamepadAction: GamepadAction? = null,
    onGamepadActionConsumed: () -> Unit = {},
    showControllerHint: Boolean = false,
    // Settings ▸ Controller ▸ Left Backs Out, mirrored in the XMB's state (see XMBUiState).
    leftBacksOut: Boolean = true,
    // Whether the last input was touch — seeds each screen's cursor visibility.
    lastInputWasTouch: Boolean = false,
    onTouchInteraction: () -> Unit = {},
    /** Runs a tapped prompt on the scaffold's footer, routed back out to the XMB so a tap and a
     *  press take the same path: a settings screen receives either through
     *  [pendingGamepadAction]. */
    onPromptTapped: ((GamepadAction) -> Unit)? = null,
    onOpenColorSchemePicker: () -> Unit = {},
    onOpenXmbLayoutAdjust: () -> Unit = {},
    onOpenCustomIcons: () -> Unit = {},
    onPreviewBootSequence: () -> Unit = {},
    onPreviewGameBoot: () -> Unit = {},
    onAddAndroidApps: () -> Unit = {},
    onOpenLibraryManager: () -> Unit = {},
    onGoToLibrary: () -> Unit = {},
    // Opens another settings screen without going back out to the crossbar first — what the
    // section rail down the left of every screen does.
    onOpenScreen: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // The overlay slot lives HERE, not in SettingsScaffold, and the reason is worth writing down:
    // a screen's prompts are drawn as SIBLINGS of its scaffold, not inside it, so that they cover
    // the header and footer rather than only the content column. A slot provided by the scaffold
    // is therefore not in scope where the prompt registers. It crashed on the device the first
    // time a converted prompt opened, which is what the error() default in the local is for --
    // a silently absent slot would have meant the prompt drew fine and quietly took no input,
    // which is the exact trap all of this is here to remove.
    //
    // Keyed on screenId so moving between screens cannot leave a departed screen's handler behind.
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
            // The automatic first-run variant: Back cannot exit from the Welcome page.
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
            // Library Manager opened straight into its Import PC Games section (games context menu).
            "settings_import_pc"  -> LibraryManagerScreen(onBack = onBack, onAddAndroidApps = onAddAndroidApps, startInImportPc = true, modifier = modifier)
            "settings_music"      -> MusicSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_video"      -> VideoSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_photo"      -> PhotoSettingsScreen(onBack = onBack, modifier = modifier)
            "settings_books"      -> BooksSettingsScreen(onBack = onBack, modifier = modifier)
            // Collections merged in here; "settings_collections" is gone from both the
            // catalog and this table.
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
