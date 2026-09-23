package com.psplauncher.feature.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.psplauncher.feature.settings.viewmodel.BackupSettingsViewModel

@Composable
fun BackupSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.restoreFromUri(it) }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { viewModel.setBackupFolder(it) } }

    SettingsPageScaffold(
        subtitle = "Backup & Restore",
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
            SettingsGroup("Backup")

            SettingsValueRow(
                label = "Last Backup",
                value = state.lastBackupDate ?: "Never",
            )

            SettingsRow(
                label    = "Backup Folder",
                sublabel = state.backupFolder?.let { "Saving to: $it  (tap to change)" }
                    ?: "Not set — tap to choose where backups are saved",
                onClick  = if (state.isWorking) null else ({ folderPicker.launch(null) }),
            )

            SettingsRow(
                label    = "Back Up Now",
                sublabel = if (state.backupFolderSet) "Saves library, settings, and play history"
                    else "Choose a backup folder first",
                onClick  = if (state.isWorking || !state.backupFolderSet) null else ({ viewModel.backupNow() }),
            )

            if (state.backupFiles.isNotEmpty()) {
                SettingsGroup("Saved Backups")
                state.backupFiles.forEach { name ->
                    SettingsValueRow(label = name, value = "")
                }
            }

            SettingsGroup("What's Included")

            listOf("Game Library", "Play History", "Custom Categories", "Settings", "Emulator Profiles")
                .forEach { included ->
                    SettingsRow(
                        label    = included,
                        trailing = { com.psplauncher.core.ui.components.PfpCheckMark(SettingsText) },
                    )
                }
            SettingsValueRow(label = "ROM Files",           value = "✗  (not included)")
            // This row used to read "Settings & API Keys ✓", which was the one claim on this
            // screen the code contradicts. A key is sealed with a Keystore key belonging to this
            // INSTALL; the ciphertext travels in the archive, but a restore that cannot decrypt it
            // drops it on purpose rather than feed the API a dead string (BackupManager's
            // ENCRYPTED_CREDENTIAL_KEYS). So the keys come back across an update, and do not come
            // back after an uninstall or onto another device — which is exactly when someone is
            // reading this list.
            SettingsValueRow(label = "API Keys",            value = "✗  (re-enter after a reinstall)")

            SettingsGroup("Restore")

            SettingsRow(
                label    = "Restore from File",
                sublabel = "Browse to a .pfpbackup file",
                onClick  = if (state.isWorking) null else ({
                    restorePicker.launch(arrayOf("*/*"))
                }),
            )

            SettingsRow(
                label    = "After Restoring",
                sublabel = "Your folders come back, but Android's access to them does not. Re-link " +
                    "each root under its section's Root Access (Library, Music, Video, Photo) — one " +
                    "tap each; re-linking a root restores everything under it at once.",
            )

            if (state.isWorking) {
                SettingsRow(
                    label    = "Working…",
                    sublabel = state.workingMessage,
                )
            }

            state.errorMessage?.let { err ->
                SettingsRow(
                    label    = "Error",
                    sublabel = err,
                    onClick  = { viewModel.dismissError() },
                )
            }
        }
    }
}
