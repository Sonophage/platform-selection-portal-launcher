package com.psplauncher.feature.xmb.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.IconDisplayMode
import com.psplauncher.core.domain.model.VideoSnapPlacement

/**
 * The ICON1 video snap of the currently focused game, non-null only while the XMBViewModel's
 * linger gate + battery/thermal gates all pass.
 *
 * [placement] rides along rather than being a second CompositionLocal because there are two
 * render sites for one decoder: the tile in [GameIcon] and the full-bleed layer in XMBShell.
 * Carried together they cannot disagree, so the snap can never be drawn in both places at once.
 */
data class FocusedGameVideo(
    val gameId: Long,
    val uri: String,
    val placement: VideoSnapPlacement = VideoSnapPlacement.ICON,
)

/** Where an approved snap is drawn. Exactly one site, because there is exactly one decoder. */
enum class SnapSite { TILE, BACKGROUND }

/**
 * THE rule for where a snap draws, and therefore whether it is worth decoding at all.
 *
 * Null means nowhere: the user asked for the snap in the tile, and this tile is not drawn in
 * [IconDisplayMode.ICON0], so there is no 144:80 slot to play it over.
 *
 * One function because three places ask the question and a disagreement between any two of them
 * is invisible: XMBViewModel asks before approving a snap, GameIconView asks before drawing one
 * in the tile, and XMBShell draws the background layer. The shell takes the shortcut of testing
 * the placement alone, which is only safe because BACKGROUND never depends on the mode --
 * asserted in SnapSiteTest rather than assumed.
 */
fun snapSiteFor(placement: VideoSnapPlacement, resolvedMode: IconDisplayMode): SnapSite? =
    when (placement) {
        VideoSnapPlacement.BACKGROUND -> SnapSite.BACKGROUND
        VideoSnapPlacement.ICON -> SnapSite.TILE.takeIf { resolvedMode == IconDisplayMode.ICON0 }
    }

// Provided by XMBShell alongside LocalXmbIconOverrides so the deeply nested tile composables
// (main list, drill flyout game column) never need the values plumbed through their params.
val LocalIconDisplayMode = compositionLocalOf { IconDisplayMode.DEFAULT }
// Per-console icon display overrides, keyed by platform id. A console absent from the map
// follows LocalIconDisplayMode; per-game overrides still beat both.
val LocalIconDisplayModeByPlatform = compositionLocalOf { emptyMap<String, IconDisplayMode>() }
val LocalFocusedGameVideo = compositionLocalOf<FocusedGameVideo?> { null }

// Live horizontal shift of the whole XMB cross (from the "Adjust XMB Layout" editor). Read by the
// category bar and item column so the cross moves left/right as one; 0.dp = the spec's anchor.
val LocalXmbHorizontalShift = compositionLocalOf { 0.dp }
