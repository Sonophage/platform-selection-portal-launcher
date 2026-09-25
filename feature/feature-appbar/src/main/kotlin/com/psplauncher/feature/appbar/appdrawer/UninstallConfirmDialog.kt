package com.psplauncher.feature.appbar.appdrawer

import androidx.compose.runtime.Composable
import com.psplauncher.core.ui.detail.PfpConfirmOverlay
import com.psplauncher.feature.appbar.InstalledApp

// ── Uninstall confirmation ────────────────────────────────────────────────────

/**
 * The app drawer's uninstall guard rail, on the app's shared confirm overlay.
 *
 * It was a hand-built panel, and it was the only confirm in the app that laid its buttons out side
 * by side with the destructive one leading and **no cursor on either**. That worked, but only
 * because the ViewModel treated every key except SELECT as a cancel: with nothing focused on
 * screen there was nothing for a D-pad to move. Giving it the shared overlay makes the prompt
 * navigable, puts Cancel first, and opens on Cancel — which is the rule every other destructive
 * prompt here already follows.
 *
 * This file used to also hold `AppDrawerOptions`, a 280dp centred panel that did the same job as
 * the right-edge menu on every other screen. The drawer now opens [PspContextMenuOverlay] like
 * everything else, so only the confirm is left and the file is named after it.
 */
@Composable
internal fun UninstallConfirmDialog(
    app: InstalledApp,
    confirmFocused: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    PfpConfirmOverlay(
        title = "Uninstall ${app.label}?",
        message = "This removes ${app.label} from your device. Android will ask you to confirm.",
        confirmLabel = "Uninstall",
        cancelLabel = "Cancel",
        confirmFocused = confirmFocused,
        cancelFocused = !confirmFocused,
        onConfirm = onConfirm,
        onCancel = onCancel,
    )
}
