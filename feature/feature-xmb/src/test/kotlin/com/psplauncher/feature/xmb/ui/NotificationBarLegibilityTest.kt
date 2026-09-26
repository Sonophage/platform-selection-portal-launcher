package com.psplauncher.feature.xmb.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBarLegibilityTest {
    @Test
    fun `no text in the notification bar falls below the floor`() {
        val floor = NotificationBarStyle.LegibilityFloorPx
        val title = NotificationBarStyle.TitleSp * NotificationBarStyle.PanelDensity
        val detail = NotificationBarStyle.DetailSp * NotificationBarStyle.PanelDensity
        assertTrue("the title renders at ${title}px, under the ${floor}px floor", title >= floor)
        assertTrue("the detail renders at ${detail}px, under the ${floor}px floor", detail >= floor)
    }

    @Test
    fun `the title is still bigger than the detail`() {
        assertTrue(
            "title ${NotificationBarStyle.TitleSp}sp is not above detail ${NotificationBarStyle.DetailSp}sp",
            NotificationBarStyle.TitleSp > NotificationBarStyle.DetailSp,
        )
    }
}
