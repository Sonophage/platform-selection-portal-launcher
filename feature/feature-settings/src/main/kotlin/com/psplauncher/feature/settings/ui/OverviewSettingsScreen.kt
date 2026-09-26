package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.psplauncher.core.common.format.formatByteSize
import com.psplauncher.feature.settings.viewmodel.OverviewSettingsViewModel

@Composable
fun OverviewSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OverviewSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }

    SettingsPageScaffold(
        subtitle = "Overview",
        onBack = onBack,
        modifier = modifier,
    ) {
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            SettingsGroup("Library")
            SettingsValueRow(label = "Games", value = countLabel(state.games, state.loading))
            SettingsValueRow(label = "Music Tracks", value = countLabel(state.tracks, state.loading))
            SettingsValueRow(label = "Books", value = countLabel(state.books, state.loading))
            SettingsValueRow(label = "Videos", value = countLabel(state.videos, state.loading))

            SettingsGroup("Artwork")
            SettingsValueRow(
                label = "Complete",
                value = "${state.artwork.complete} of ${state.artwork.total}",
                sublabel = "Games whose box art resolves to a file that is actually there",
            )

            if (state.artwork.missing > 0) {
                SettingsValueRow(label = "Missing", value = state.artwork.missing.toString())
            }
            if (state.artwork.stale > 0) {
                SettingsValueRow(
                    label = "Stale",
                    value = state.artwork.stale.toString(),
                    sublabel = "Referenced art whose file has gone",
                )
            }
            SettingsValueRow(
                label = "Cache",
                value = state.artworkCacheBytes?.let { formatByteSize(it) } ?: "Measuring…",
            )

            SettingsGroup("Build")
            SettingsValueRow(label = "Version", value = packageInfo?.versionName ?: "unknown")
            SettingsValueRow(label = "Build", value = packageInfo?.longVersionCode?.toString() ?: "unknown")
        }
    }
}

private fun countLabel(count: Int, loading: Boolean): String = if (loading) "—" else count.toString()
