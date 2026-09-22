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

/**
 * Where Settings opens.
 *
 * It used to open on Library Manager, because the landing screen was "the first entry in the
 * catalogue" and Library Manager happened to be first. That put the user inside a specific
 * screen — with its ROM roots and its delete buttons — before they had chosen anything, and it
 * expanded the Library section of the rail as a side effect.
 *
 * This is the first entry now, so the same "land on the first screen" rule lands somewhere that
 * is about the launcher rather than about one part of it, and the rail sits unexpanded beside it.
 *
 * Every number is derived on open rather than stored. See OverviewSettingsViewModel.
 */
@Composable
fun OverviewSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OverviewSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The installed version, from PackageManager — a library module's BuildConfig cannot know the
    // app's versionName, and the hardcoded copy that used to live in About went stale.
    val context = LocalContext.current
    val packageInfo = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }

    // PILOT for the wizard skin across all of Settings (see SettingsPageScaffold). Overview first
    // because it is where Settings lands, so it is the screen that sets the expectation.
    //
    // The hint is the catalog's own subtitle for this screen, quoted rather than referenced: the
    // wiring that hands every screen its SettingsEntry belongs with the rollout, not with the one
    // page proving the look.
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
            // Only shown when there is something to act on: a row reading "Missing 0" is a row
            // asking to be read every time it says nothing.
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

/**
 * A count, or an em dash while the first emission is still on its way.
 *
 * Zero and "not counted yet" are different facts and must not share a rendering: a library that
 * has genuinely nothing in it should say 0, and a page that has not finished asking should not
 * claim it did. This screen exists partly because "Total Games 0" was reported once and could
 * not be reproduced, which is exactly the shape of a loading state read as an answer.
 */
private fun countLabel(count: Int, loading: Boolean): String = if (loading) "—" else count.toString()
