package com.psplauncher.feature.xmb.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.psplauncher.core.domain.model.Game
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.preview.PfpScreenPreview
import com.psplauncher.feature.artwork.match.GameCandidate
import com.psplauncher.feature.artwork.match.GameMatch
import com.psplauncher.feature.artwork.match.MatchProvider
import com.psplauncher.feature.artwork.match.MatchTier

private object PreviewStudioActions : ArtworkStudioActions {
    override fun handleGamepadAction(action: GamepadAction) = Unit
    override fun selectTab(index: Int) = Unit
    override fun sourcesForTab(): List<StudioSource> = StudioSource.entries
    override fun sourceBadge(source: StudioSource): String? = null
    override fun selectSource(index: Int) = Unit
    override fun requestLocalPick() = Unit
    override fun toggleNsfw() = Unit
    override fun openSearch() = Unit
    override fun onQueryDraftChanged(text: String) = Unit
    override fun submitSearch() = Unit
    override fun cancelSearch() = Unit
    override fun resetSearchToTitle() = Unit
    override fun onChangeMatchPressed() = Unit
    override fun onChangeMatchDraftChanged(text: String) = Unit
    override fun startChangeMatchEdit() = Unit
    override fun stopChangeMatchEdit() = Unit
    override fun submitChangeMatch() = Unit
    override fun confirmMatch(index: Int) = Unit
    override fun cancelChangeMatch() = Unit
    override fun forgetMatch() = Unit
    override fun onGridMeasured(widthDp: Float, heightDp: Float) = Unit
    override fun openCandidate(index: Int) = Unit
    override fun toggleSelection(index: Int) = Unit
    override fun previousPage() = Unit
    override fun nextPage() = Unit
    override fun applyChanges() = Unit
    override fun resolveConfirm(index: Int) = Unit
    override fun dismissConfirm() = Unit
    override fun retryFailed() = Unit
    override fun removeFailed() = Unit
    override fun openAssetManager() = Unit
    override fun closeAssetManager() = Unit
    override fun focusManagedAsset(index: Int) = Unit
    override fun moveManagedAsset(delta: Int) = Unit
    override fun makeManagedAssetPrimary() = Unit
    override fun resolveLeavePrompt(choice: StudioLeaveChoice) = Unit
    override fun applyCandidate() = Unit
    override fun dismissCandidate() = Unit
    override fun onManualPageCount(count: Int) = Unit
    override fun manualPreviousPage() = Unit
    override fun manualNextPage() = Unit
    override fun openActions() = Unit
    override fun closeActions() = Unit
    override fun runAction(action: StudioAction) = Unit
    override fun panCrop(dx: Float, dy: Float) = Unit
    override fun zoomCrop(factor: Float) = Unit
    override fun applyCrop() = Unit
    override fun cancelCrop() = Unit
    override fun toggleCropPreview() = Unit
    override fun openCropOptions() = Unit
    override fun closeCropOptions() = Unit
    override fun moveCropOptionsCursor(delta: Int) = Unit
    override fun activateCropOption(index: Int) = Unit
    override fun dismissMessage() = Unit
}

private const val SAMPLE_TOTAL_RESULTS = 23

private fun sampleStudioState(slotWidthDp: Float, slotHeightDp: Float): ArtworkStudioUiState {
    val tabIndex = 0
    val capacity = StudioGridCapacity.of(slotWidthDp, slotHeightDp, STUDIO_TABS[tabIndex].tileClass)
    val pageSize = capacity.pageSize
    val onPage = minOf(pageSize, SAMPLE_TOTAL_RESULTS)
    return ArtworkStudioUiState(
        game = Game(id = 1, title = "Tactics Ogre: Reborn", platformId = "windows"),
        isLoading = false,
        tabIndex = tabIndex,
        sourceIndex = StudioSource.entries.indexOf(StudioSource.STEAMGRIDDB),
        zone = StudioZone.GRID,
        gridIndex = minOf(6, onPage - 1),
        gridColumns = capacity.columns,
        gridRows = capacity.rows,
        page = 0,
        pageCount = (SAMPLE_TOTAL_RESULTS + pageSize - 1) / pageSize,
        rangeStart = 1,
        rangeEnd = onPage,
        totalResults = SAMPLE_TOTAL_RESULTS,
        results = List(onPage) { i ->
            StudioArt(
                url = "https://preview.invalid/grid_$i.png",
                thumb = null,
                provider = "SteamGridDB",
                label = if (i % 4 == 3) "white_logo · 660×930" else "alternate · 600×900",
            )
        },
        query = "Tactics Ogre: Reborn",
        match = GameMatch(
            candidate = GameCandidate(MatchProvider.STEAMGRIDDB, providerGameId = "5323", title = "Tactics Ogre: Reborn"),
            tier = MatchTier.EXACT_TITLE,
        ),
        matchProvider = MatchProvider.STEAMGRIDDB,
        hasSgdbKey = true,
    )
}

@Composable
private fun StudioPreview(slotWidthDp: Float, slotHeightDp: Float, showTouchControls: Boolean) {
    PfpScreenPreview {
        ArtworkStudioContent(
            state = sampleStudioState(slotWidthDp, slotHeightDp),
            actions = PreviewStudioActions,
            showTouchControls = showTouchControls,
            onTouchInput = {},
        )
    }
}

@Preview(name = "AYN Thor · controller", widthDp = 833, heightDp = 468, group = "Artwork Studio")
@Composable
private fun ArtworkStudioThorControllerPreview() {
    StudioPreview(slotWidthDp = 613f, slotHeightDp = 285f, showTouchControls = false)
}

@Preview(name = "AYN Thor · touch", widthDp = 833, heightDp = 468, group = "Artwork Studio")
@Composable
private fun ArtworkStudioThorTouchPreview() {
    StudioPreview(slotWidthDp = 613f, slotHeightDp = 261f, showTouchControls = true)
}

@Preview(name = "20:9 phone · controller", widthDp = 915, heightDp = 412, group = "Artwork Studio")
@Composable
private fun ArtworkStudioPhonePreview() {
    StudioPreview(slotWidthDp = 717f, slotHeightDp = 203f, showTouchControls = false)
}

@Preview(name = "16:10 tablet · controller", widthDp = 1280, heightDp = 800, group = "Artwork Studio")
@Composable
private fun ArtworkStudioTabletPreview() {
    StudioPreview(slotWidthDp = 1032f, slotHeightDp = 591f, showTouchControls = false)
}
