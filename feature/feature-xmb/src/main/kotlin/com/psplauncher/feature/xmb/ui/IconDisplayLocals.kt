package com.psplauncher.feature.xmb.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.IconDisplayMode
import com.psplauncher.core.domain.model.VideoSnapPlacement

data class FocusedGameVideo(
    val gameId: Long,
    val uri: String,
    val placement: VideoSnapPlacement = VideoSnapPlacement.ICON,
)

enum class SnapSite { TILE, BACKGROUND, PANEL }

fun snapSiteFor(
    placement: VideoSnapPlacement,
    resolvedMode: IconDisplayMode,
    panelShowingVideo: Boolean,
): SnapSite? = when {
    panelShowingVideo -> SnapSite.PANEL
    placement == VideoSnapPlacement.BACKGROUND -> SnapSite.BACKGROUND
    else -> SnapSite.TILE.takeIf { resolvedMode == IconDisplayMode.ICON0 }
}

fun shellSnapSite(placement: VideoSnapPlacement, panelShowingVideo: Boolean): SnapSite? =
    snapSiteFor(placement, IconDisplayMode.ICON0, panelShowingVideo)

val LocalIconDisplayMode = compositionLocalOf { IconDisplayMode.DEFAULT }

val LocalIconDisplayModeByPlatform = compositionLocalOf { emptyMap<String, IconDisplayMode>() }
val LocalFocusedGameVideo = compositionLocalOf<FocusedGameVideo?> { null }

val LocalPanelShowingVideo = compositionLocalOf { false }

val LocalXmbHorizontalShift = compositionLocalOf { 0.dp }
