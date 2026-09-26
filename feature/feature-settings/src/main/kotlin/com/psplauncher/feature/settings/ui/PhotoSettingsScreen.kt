package com.psplauncher.feature.settings.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.psplauncher.core.ui.preview.CombinedPreviews
import com.psplauncher.core.ui.preview.PfpScreenPreview
import com.psplauncher.feature.settings.viewmodel.PhotoSettingsUiState
import com.psplauncher.feature.settings.viewmodel.PhotoSettingsViewModel
import com.psplauncher.feature.settings.viewmodel.RootFolderRow

@Composable
fun PhotoSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PhotoSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    val addRootPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { viewModel.addRoot(it) } }
    var relinkTarget by remember { mutableStateOf<String?>(null) }
    val relinkRootPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val old = relinkTarget
        relinkTarget = null
        if (uri != null && old != null) viewModel.relinkRoot(old, uri)
    }

    PhotoSettingsContent(
        state        = state,
        onBack       = onBack,
        onAddRoot    = { addRootPicker.launch(null) },
        onRelinkRoot = { row ->
            relinkTarget = row.treeUri
            relinkRootPicker.launch(runCatching { Uri.parse(row.treeUri) }.getOrNull())
        },
        onRemoveRoot = { viewModel.removeRoot(it.treeUri) },
        onRescan     = viewModel::rescan,
        onClearCache = viewModel::clearThumbnailCache,
        modifier     = modifier,
    )
}

@Composable
fun PhotoSettingsContent(
    state: PhotoSettingsUiState,
    onBack: () -> Unit,
    onAddRoot: () -> Unit,
    onRelinkRoot: (RootFolderRow) -> Unit,
    onRemoveRoot: (RootFolderRow) -> Unit,
    onRescan: () -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageScaffold(
        subtitle = "Photo",
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
            RootAccessSection(
                groupTitle  = "Root Folders",
                roots       = state.roots,
                addLabel    = "Add Photo Root",
                addSublabel = "Grant a root folder (e.g. /Pictures) — add several to span locations",
                emptyLabel   = "No photo folders yet",
                rootKindLabel = "Photo folder",
                onAddRoot   = onAddRoot,
                onRelinkRoot = onRelinkRoot,
                onRemoveRoot = onRemoveRoot,
            )

            SettingsRow(
                label    = "Rescan Photo Library",
                sublabel = when {
                    state.scanning            -> "Scanning…"
                    state.scanMessage != null -> state.scanMessage
                    else                      -> "Update the libraries from every root folder"
                },
                focusKey = "photo_rescan",
                onClick  = if (state.scanning || !state.hasRoots) null else onRescan,
            )

            if (state.scanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 4.dp),
                )
            }

            SettingsGroup("Maintenance")

            SettingsRow(
                label    = "Clear Thumbnail Cache",
                sublabel = "Delete generated thumbnails. A rescan regenerates them.",
                onClick  = onClearCache,
            )
        }
    }
}

@CombinedPreviews
@Composable
private fun PhotoSettingsContentPreview() {
    PfpScreenPreview {
        PhotoSettingsContent(
            state = PhotoSettingsUiState(
                roots = listOf(
                    RootFolderRow("content://preview/tree/primary%3ADCIM", "DCIM/Camera", linked = true),
                ),
            ),
            onBack       = {},
            onAddRoot    = {},
            onRelinkRoot = {},
            onRemoveRoot = {},
            onRescan     = {},
            onClearCache = {},
        )
    }
}
