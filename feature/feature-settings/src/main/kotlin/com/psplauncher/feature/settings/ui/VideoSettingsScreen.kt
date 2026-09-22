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
import com.psplauncher.core.ui.preview.PfpPreview
import com.psplauncher.feature.settings.viewmodel.RootFolderRow
import com.psplauncher.feature.settings.viewmodel.VideoSettingsUiState
import com.psplauncher.feature.settings.viewmodel.VideoSettingsViewModel

@Composable
fun VideoSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Pickers: one for adding a root, one pre-pointed at the root being re-linked (re-granting
    // after a restore/reinstall lands on the exact same folder in one tap).
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

    VideoSettingsContent(
        state = state,
        onBack = onBack,
        onAddRoot = { addRootPicker.launch(null) },
        onRelinkRoot = { row ->
            relinkTarget = row.treeUri
            relinkRootPicker.launch(runCatching { Uri.parse(row.treeUri) }.getOrNull())
        },
        onRemoveRoot = { viewModel.removeRoot(it.treeUri) },
        onRescan = viewModel::rescan,
        onOpenPlayerPicker = viewModel::openPlayerPicker,
        onTmdbKeyDraft = viewModel::setTmdbKeyDraft,
        onSaveTmdbKey = viewModel::saveTmdbKey,
        onFetchPosters = { refresh -> viewModel.fetchPosters(refresh) },
        onClearPosters = viewModel::clearPosters,
        onDismissPlayerPicker = viewModel::dismissPlayerPicker,
        onChoosePlayer = viewModel::chooseDefaultPlayer,
        onDismissMessage = viewModel::dismissMessage,
        modifier = modifier,
    )
}

@Composable
fun VideoSettingsContent(
    state: VideoSettingsUiState,
    onBack: () -> Unit,
    onAddRoot: () -> Unit,
    onRelinkRoot: (RootFolderRow) -> Unit,
    onRemoveRoot: (RootFolderRow) -> Unit,
    onRescan: () -> Unit,
    onOpenPlayerPicker: () -> Unit,
    onTmdbKeyDraft: (String) -> Unit = {},
    onSaveTmdbKey: () -> Unit = {},
    onFetchPosters: (Boolean) -> Unit = {},
    onClearPosters: () -> Unit = {},
    onDismissPlayerPicker: () -> Unit,
    onChoosePlayer: (String?) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageScaffold(
        subtitle = "Video",
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
                addLabel    = "Add Video Root",
                addSublabel = "Grant a root folder (e.g. /Movies) — add several to span locations",
                emptyLabel   = "No video folders yet",
                rootKindLabel = "Video folder",
                onAddRoot   = onAddRoot,
                onRelinkRoot = onRelinkRoot,
                onRemoveRoot = onRemoveRoot,
            )

            SettingsRow(
                label    = "Rescan Video Library",
                sublabel = when {
                    state.scanning            -> "Scanning…"
                    state.scanMessage != null -> state.scanMessage
                    else                      -> "Update the libraries from every root folder"
                },
                focusKey = "video_rescan",
                onClick  = if (state.scanning || !state.hasRoots) null else onRescan,
            )

            if (state.scanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 4.dp),
                )
            }

            // ── Posters ───────────────────────────────────────────────────────
            //
            // The scanner's thumbnail is a frame grabbed out of the file, which for a film is
            // usually a dark still of nothing. TMDB matches on the title and year the filename
            // already carries (see MovieFileName) and gives each one its poster.
            SettingsGroup("Posters")

            SettingsTextFieldRow(
                label         = if (state.hasTmdbKey) "TMDB API Key (saved)" else "TMDB API Key",
                value         = state.tmdbKeyDraft,
                onValueChange = onTmdbKeyDraft,
                placeholder   = if (state.hasTmdbKey) "••••••••  (tap to replace)" else "Paste your TMDB key",
                isPassword    = true,
                helper        = "Get a free key at themoviedb.org/settings/api",
            )

            if (state.tmdbKeyDraft.isNotBlank()) {
                SettingsRow(label = "Save TMDB Key", onClick = onSaveTmdbKey)
            }

            if (state.hasTmdbKey) {
                SettingsRow(
                    label    = "Match Posters",
                    sublabel = when {
                        state.matchingPosters      -> "Matching…"
                        state.posterMessage != null -> state.posterMessage
                        else -> "Find a poster for every film that does not have one"
                    },
                    focusKey = "video_match_posters",
                    onClick  = if (state.matchingPosters) null else ({ onFetchPosters(false) }),
                )
                SettingsRow(
                    label    = "Re-match All Posters",
                    sublabel = "Match again, including films that already have one",
                    onClick  = if (state.matchingPosters) null else ({ onFetchPosters(true) }),
                )
                SettingsRow(
                    label    = "Clear Posters",
                    sublabel = "Go back to the thumbnails taken from the files",
                    onClick  = if (state.matchingPosters) null else ({ onClearPosters() }),
                )
            } else {
                SettingsRow(
                    label    = "Match Posters",
                    sublabel = "Add a TMDB key above first",
                    onClick  = null,
                )
            }

            SettingsGroup("Playback")

            SettingsValueRow(
                label    = "Default Video Player",
                sublabel = "PSPLauncher plays in-app; or pick an app / be asked each time.",
                value    = state.defaultPlayerLabel,
                focusKey = "video_default_player",
                onClick  = onOpenPlayerPicker,
            )
        }
    }

    // ── Default player picker: PSPLauncher / System Default / an installed app ──
    if (state.showPlayerPicker) {
        // The rows and the values they set, as one list: the overlay reports an index, and an
        // index into two lists that were built separately is how a picker sets the wrong thing.
        val choices = buildList {
            add("PSPLauncher" to "builtin")
            add("System Default" to "ask")
            state.availablePlayers.forEach { add(it.label to it.packageName) }
        }
        val current = when (state.defaultPlayer) {
            null, "builtin" -> 0
            else -> choices.indexOfFirst { it.second == state.defaultPlayer }
        }
        SettingsChoiceOverlay(
            title = "Default Video Player",
            options = choices.map { it.first },
            selectedIndex = current,
            onPick = { onChoosePlayer(choices[it].second) },
            onCancel = onDismissPlayerPicker,
        )
    }
}

@Composable
private fun PlayerChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = (if (selected) "● " else "○ ") + label,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@CombinedPreviews
@Composable
private fun VideoSettingsContentPreview() {
    PfpPreview {
        VideoSettingsContent(
            state = VideoSettingsUiState(
                roots = listOf(
                    RootFolderRow("content://preview/tree/primary%3AMovies", "Movies", linked = true),
                ),
            ),
            onBack = {},
            onAddRoot = {},
            onRelinkRoot = {},
            onRemoveRoot = {},
            onRescan = {},
            onOpenPlayerPicker = {},
            onDismissPlayerPicker = {},
            onChoosePlayer = {},
            onDismissMessage = {},
        )
    }
}
