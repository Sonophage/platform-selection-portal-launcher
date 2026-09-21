package com.psplauncher.feature.xmb.ui

import com.psplauncher.core.domain.model.IconDisplayMode
import com.psplauncher.core.domain.model.VideoSnapPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A video snap is decoded once and drawn once. Four places decide that: XMBViewModel approves it,
 * GameIconView draws it in the tile, XMBShell draws it behind the crossbar and hands it to the
 * hover panel. They agree only because they all call [snapSiteFor] — these tests are what stops
 * that decaying into four copies of the rule, where a mismatch shows up as a snap that silently
 * never plays, or as two decoders on one file.
 */
class SnapSiteTest {

    @Test
    fun `the icon placement needs an ICON0 tile to play over`() {
        assertEquals(
            SnapSite.TILE,
            snapSiteFor(VideoSnapPlacement.ICON, IconDisplayMode.ICON0, panelShowingVideo = false),
        )
        IconDisplayMode.entries.filter { it != IconDisplayMode.ICON0 }.forEach { mode ->
            assertNull(
                snapSiteFor(VideoSnapPlacement.ICON, mode, panelShowingVideo = false),
                "$mode draws artwork at natural aspect, so there is no 144:80 slot to play over",
            )
        }
    }

    @Test
    fun `the background placement plays in every icon mode`() {
        // XMBShell tests the placement alone, without a resolved mode, because it has none to
        // hand. That shortcut is only correct while this holds for EVERY mode, so it is asserted
        // over all of them rather than the one the author happened to be looking at.
        IconDisplayMode.entries.forEach { mode ->
            assertEquals(
                SnapSite.BACKGROUND,
                snapSiteFor(VideoSnapPlacement.BACKGROUND, mode, panelShowingVideo = false),
                "the background layer has no tile, so $mode must not veto it",
            )
        }
    }

    @Test
    fun `the panel's video page takes the clip from whichever site would have had it`() {
        // The user walked the panel onto Video. That is an explicit request to look at the clip,
        // and there is one decoder, so both older sites must stand down — including the icon
        // placement in a mode that would otherwise have refused the snap entirely.
        VideoSnapPlacement.entries.forEach { placement ->
            IconDisplayMode.entries.forEach { mode ->
                assertEquals(
                    SnapSite.PANEL,
                    snapSiteFor(placement, mode, panelShowingVideo = true),
                    "$placement/$mode must yield the clip to the open panel",
                )
            }
        }
    }

    @Test
    fun `the shell's mode-free shortcut agrees with the full rule on the sites it owns`() {
        // XMBShell has no resolved icon mode, so it calls shellSnapSite, which supplies one.
        // That is only safe while neither of the shell's own sites depends on the mode. Checked
        // against every mode rather than asserted in a comment.
        listOf(true, false).forEach { panelShowingVideo ->
            VideoSnapPlacement.entries.forEach { placement ->
                val shortcut = shellSnapSite(placement, panelShowingVideo)
                IconDisplayMode.entries.forEach { mode ->
                    val full = snapSiteFor(placement, mode, panelShowingVideo)
                    if (full == SnapSite.BACKGROUND || full == SnapSite.PANEL || shortcut == SnapSite.BACKGROUND || shortcut == SnapSite.PANEL) {
                        assertEquals(
                            full,
                            shortcut,
                            "$placement/$mode/panel=$panelShowingVideo: the shell would draw the wrong thing",
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `exactly one renderer draws, for every combination there is`() {
        // The version of this test that this replaces asked whether one value equalled two
        // different constants at once, which no value can, so it could not fail. This one asks
        // each render site the question the way that site asks it, and counts the yeses.
        listOf(true, false).forEach { panelShowingVideo ->
            VideoSnapPlacement.entries.forEach { placement ->
                IconDisplayMode.entries.forEach { mode ->
                    val site = snapSiteFor(placement, mode, panelShowingVideo)
                    val drawing = listOf(
                        // GameIconView
                        site == SnapSite.TILE,
                        // XMBShell's full-bleed layer
                        shellSnapSite(placement, panelShowingVideo) == SnapSite.BACKGROUND,
                        // The hover panel's video page
                        site == SnapSite.PANEL,
                    ).count { it }
                    assertTrue(
                        drawing <= 1,
                        "$placement/$mode/panel=$panelShowingVideo draws in $drawing places at once",
                    )
                }
            }
        }
    }
}
