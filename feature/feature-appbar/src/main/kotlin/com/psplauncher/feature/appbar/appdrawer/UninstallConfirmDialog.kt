package com.psplauncher.feature.appbar.appdrawer

import androidx.compose.runtime.Composable
import com.psplauncher.core.ui.detail.PfpConfirmOverlay
import com.psplauncher.feature.appbar.InstalledApp

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
