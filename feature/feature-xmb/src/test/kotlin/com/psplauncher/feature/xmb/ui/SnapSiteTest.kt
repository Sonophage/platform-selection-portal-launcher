package com.psplauncher.feature.xmb.ui

import com.psplauncher.core.domain.model.IconDisplayMode
import com.psplauncher.core.domain.model.VideoSnapPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        listOf(true, false).forEach { panelShowingVideo ->
            VideoSnapPlacement.entries.forEach { placement ->
                IconDisplayMode.entries.forEach { mode ->
                    val site = snapSiteFor(placement, mode, panelShowingVideo)
                    val drawing = listOf(

                        site == SnapSite.TILE,

                        shellSnapSite(placement, panelShowingVideo) == SnapSite.BACKGROUND,

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
