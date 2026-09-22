package com.psplauncher.feature.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.psplauncher.feature.settings.viewmodel.BooksSettingsUiState
import com.psplauncher.feature.settings.viewmodel.BooksSettingsViewModel
import com.psplauncher.feature.settings.viewmodel.RootFolderRow

/**
 * Stateful entry point: owns the ViewModel, collects its state, and wires the folder pickers.
 * Thin on purpose, so the previewable UI lives in [BooksSettingsContent].
 */
@Composable
fun BooksSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BooksSettingsViewModel = hiltViewModel(),
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

    BooksSettingsContent(
        state                 = state,
        onBack                = onBack,
        onAddRoot             = { addRootPicker.launch(null) },
        onRelinkRoot          = { row ->
            relinkTarget = row.treeUri
            relinkRootPicker.launch(runCatching { Uri.parse(row.treeUri) }.getOrNull())
        },
        onRemoveRoot          = { viewModel.removeRoot(it.treeUri) },
        onRescan              = { viewModel.rescan(deep = false) },
        onDeepRescan          = { viewModel.rescan(deep = true) },
        onClearCoverCache     = viewModel::clearCoverCache,
        onOpenReaderPicker    = viewModel::openReaderPicker,
        onDismissReaderPicker = viewModel::dismissReaderPicker,
        onChooseReader        = viewModel::chooseReader,
        modifier              = modifier,
    )
}

/** Stateless UI, driven purely by [state] and callbacks, so it renders in `@Preview`. */
@Composable
fun BooksSettingsContent(
    state: BooksSettingsUiState,
    onBack: () -> Unit,
    onAddRoot: () -> Unit,
    onRelinkRoot: (RootFolderRow) -> Unit,
    onRemoveRoot: (RootFolderRow) -> Unit,
    onRescan: () -> Unit,
    onDeepRescan: () -> Unit,
    onClearCoverCache: () -> Unit,
    onOpenReaderPicker: () -> Unit,
    onDismissReaderPicker: () -> Unit,
    onChooseReader: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageScaffold(
        subtitle = "Books",
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
                groupTitle   = "Root Folders",
                roots        = state.roots,
                addLabel     = "Add Book Root",
                addSublabel  = "Grant a root folder (e.g. /Books) — add several to span locations",
                emptyLabel   = "No book folders yet",
                rootKindLabel = "Book folder",
                onAddRoot    = onAddRoot,
                onRelinkRoot = onRelinkRoot,
                onRemoveRoot = onRemoveRoot,
            )

            SettingsRow(
                label    = "Rescan Library",
                sublabel = when {
                    state.scanning            -> "Scanning…"
                    state.scanMessage != null -> state.scanMessage
                    else                      -> "Read new and changed books only"
                },
                focusKey = "books_rescan",
                onClick  = if (state.scanning || !state.hasRoots) null else onRescan,
            )

            // Reading a book's series and cover means opening the archive, so a normal rescan
            // skips books whose file has not changed. This is the way back in when the metadata
            // was edited without the timestamp moving, or when a cover looks wrong.
            SettingsRow(
                label    = "Deep Rescan",
                sublabel = "Reopen every book and rebuild every cover. Slow on a large library.",
                focusKey = "books_deep_rescan",
                onClick  = if (state.scanning || !state.hasRoots) null else onDeepRescan,
            )

            if (state.scanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 4.dp),
                )
            }

            SettingsGroup("Reading")

            SettingsValueRow(
                label    = "Default Reader",
                sublabel = "The app a book opens in. There is no in-app reader.",
                value    = state.defaultReaderLabel,
                focusKey = "books_default_reader",
                onClick  = onOpenReaderPicker,
            )

            SettingsGroup("Maintenance")

            SettingsRow(
                label    = "Clear Cover Cache",
                sublabel = "Delete the extracted covers. A rescan regenerates them.",
                focusKey = "books_clear_covers",
                onClick  = onClearCoverCache,
            )
        }
    }

    // ── Reader picker: Ask Every Time, or an installed reader ──────────────────
    // No built-in choice, unlike Music and Video: this launcher does not read EPUBs.
    if (state.showReaderPicker) {
        // Label and value together, so the index the overlay reports cannot address one list
        // while meaning the other.
        val choices: List<Pair<String, String?>> = buildList {
            add("Ask Every Time" to null)
            state.availableReaders.forEach { add(it.label to it.packageName) }
        }
        SettingsChoiceOverlay(
            title = "Default Reader",
            options = choices.map { it.first },
            selectedIndex = choices.indexOfFirst { it.second == state.defaultReader },
            onPick = { onChooseReader(choices[it].second) },
            onCancel = onDismissReaderPicker,
        )
    }
}

@Composable
private fun ReaderChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = (if (selected) "● " else "○ ") + label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@CombinedPreviews
@Composable
private fun BooksSettingsContentPreview() {
    PfpScreenPreview {
        BooksSettingsContent(
            state = BooksSettingsUiState(
                roots = listOf(
                    RootFolderRow("content://preview/tree/primary%3ABooks", "Books", linked = true),
                ),
            ),
            onBack                = {},
            onAddRoot             = {},
            onRelinkRoot          = {},
            onRemoveRoot          = {},
            onRescan              = {},
            onDeepRescan          = {},
            onClearCoverCache     = {},
            onOpenReaderPicker    = {},
            onDismissReaderPicker = {},
            onChooseReader        = {},
        )
    }
}
