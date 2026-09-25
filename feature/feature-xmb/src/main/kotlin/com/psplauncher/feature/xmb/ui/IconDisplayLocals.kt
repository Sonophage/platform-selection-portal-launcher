package com.psplauncher.feature.xmb.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp
import com.psplauncher.core.domain.model.IconDisplayMode
import com.psplauncher.core.domain.model.VideoSnapPlacement

/**
 * The ICON1 video snap of the currently focused game, non-null only while the XMBViewModel's
 * linger gate + battery/thermal gates all pass.
 *
 * [placement] rides along rather than being a second CompositionLocal because there are three
 * render sites for one decoder: the tile in [GameIcon], the full-bleed layer in XMBShell, and the
 * hover panel's video page. Carried together they cannot disagree, so the snap can never be drawn
 * in more than one of them at once.
 */
data class FocusedGameVideo(
    val gameId: Long,
    val uri: String,
    val placement: VideoSnapPlacement = VideoSnapPlacement.ICON,
)

/** Where an approved snap is drawn. Exactly one site, because there is exactly one decoder. */
enum class SnapSite { TILE, BACKGROUND, PANEL }

/**
 * THE rule for where a snap draws, and therefore whether it is worth decoding at all.
 *
 * Null means nowhere: the user asked for the snap in the tile, and this tile is not drawn in
 * [IconDisplayMode.ICON0], so there is no 144:80 slot to play it over.
 *
 * One function because four places ask the question and a disagreement between any two of them
 * is invisible: XMBViewModel asks before approving a snap at all, GameIconView asks before
 * drawing one in the tile, and XMBShell asks for the background layer and for the panel.
 *
 * [panelShowingVideo] outranks both older sites. The user walked the hover panel onto its video
 * page, which is an explicit request to look at the clip, and there is still one decoder -- so
 * the tile and the background yield rather than a second ExoPlayer opening on the same uri.
 *
 * No default on [panelShowingVideo], deliberately: a defaulted false would let a new call site
 * quietly keep the old two-site behaviour and open that second player.
 */
fun snapSiteFor(
    placement: VideoSnapPlacement,
    resolvedMode: IconDisplayMode,
    panelShowingVideo: Boolean,
): SnapSite? = when {
    panelShowingVideo -> SnapSite.PANEL
    placement == VideoSnapPlacement.BACKGROUND -> SnapSite.BACKGROUND
    else -> SnapSite.TILE.takeIf { resolvedMode == IconDisplayMode.ICON0 }
}

/**
 * The shell's view of the rule. It has no resolved icon mode to hand, and does not need one:
 * neither BACKGROUND nor PANEL depends on the mode, which SnapSiteTest asserts over every mode
 * rather than leaving to this comment. Any mode therefore gives the right answer for the two
 * sites the shell owns, and ICON0 is passed because it is the one that can answer TILE -- so if
 * this ever stops being mode-independent, the shell errs toward NOT drawing rather than toward
 * drawing twice.
 */
fun shellSnapSite(placement: VideoSnapPlacement, panelShowingVideo: Boolean): SnapSite? =
    snapSiteFor(placement, IconDisplayMode.ICON0, panelShowingVideo)

// Provided by XMBShell alongside LocalXmbIconOverrides so the deeply nested tile composables
// (main list, drill flyout game column) never need the values plumbed through their params.
val LocalIconDisplayMode = compositionLocalOf { IconDisplayMode.DEFAULT }
// Per-console icon display overrides, keyed by platform id. A console absent from the map
// follows LocalIconDisplayMode; per-game overrides still beat both.
val LocalIconDisplayModeByPlatform = compositionLocalOf { emptyMap<String, IconDisplayMode>() }
val LocalFocusedGameVideo = compositionLocalOf<FocusedGameVideo?> { null }

/**
 * True while the hover panel is on its video page, so the snap belongs to the panel and no other
 * site may draw it. A CompositionLocal for the same reason as the two above: the tile composables
 * that must stand down are several layers below the shell that knows.
 */
val LocalPanelShowingVideo = compositionLocalOf { false }

// Live horizontal shift of the whole XMB cross (from the "Adjust XMB Layout" editor). Read by the
// category bar and item column so the cross moves left/right as one; 0.dp = the spec's anchor.
val LocalXmbHorizontalShift = compositionLocalOf { 0.dp }
