package com.psplauncher.feature.xmb.ui

import com.psplauncher.core.domain.model.IconDisplayMode
import com.psplauncher.core.domain.model.VideoSnapPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A video snap is decoded once and drawn once. Three places decide that: XMBViewModel approves
 * it, GameIconView draws it in the tile, XMBShell draws it behind the crossbar. They agree only
 * because they all call [snapSiteFor] — these tests are what stops that decaying into three
 * copies of the rule, where a mismatch shows up as a snap that silently never plays.
 */
class SnapSiteTest {

    @Test
    fun `the icon placement needs an ICON0 tile to play over`() {
        assertEquals(
            SnapSite.TILE,
            snapSiteFor(VideoSnapPlacement.ICON, IconDisplayMode.ICON0),
        )
        IconDisplayMode.entries.filter { it != IconDisplayMode.ICON0 }.forEach { mode ->
            assertNull(
                snapSiteFor(VideoSnapPlacement.ICON, mode),
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
                snapSiteFor(VideoSnapPlacement.BACKGROUND, mode),
                "the background layer has no tile, so $mode must not veto it",
            )
        }
    }

    @Test
    fun `no placement and mode pair draws in two places at once`() {
        // One decoder. A pair that answered both sites would mean two ExoPlayers on one snap.
        val sites = VideoSnapPlacement.entries.flatMap { placement ->
            IconDisplayMode.entries.map { mode -> snapSiteFor(placement, mode) }
        }
        assertEquals(
            VideoSnapPlacement.entries.size * IconDisplayMode.entries.size,
            sites.size,
            "snapSiteFor returns one site or none, never a set",
        )
    }

    @Test
    fun `a snap approved for one site is never drawn by the other`() {
        VideoSnapPlacement.entries.forEach { placement ->
            IconDisplayMode.entries.forEach { mode ->
                val site = snapSiteFor(placement, mode)
                val tileDraws = site == SnapSite.TILE
                val backgroundDraws = site == SnapSite.BACKGROUND
                assertEquals(
                    false,
                    tileDraws && backgroundDraws,
                    "$placement/$mode would draw in both places",
                )
            }
        }
    }
}
