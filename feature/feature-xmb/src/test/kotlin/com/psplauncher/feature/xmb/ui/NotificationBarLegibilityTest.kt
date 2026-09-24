package com.psplauncher.feature.xmb.ui

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bundle's legibility floor, now applied where the sentences moved to.
 *
 * "No text below 28 px at native res" is the design's own system rule. It used to be checked
 * against the toast card; that card is gone and its text is here, so the rule came with it. The
 * status strip is deliberately NOT covered — a clock you glance at and a report you read are not
 * the same kind of text, and the strip has been under the floor since before any of this.
 */
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
        // The floor pushes the detail UP, and the detail is the line closest to it. Raise the
        // floor far enough and it meets the title, at which point the row has one type size and no
        // hierarchy — and the assertion above would still pass.
        assertTrue(
            "title ${NotificationBarStyle.TitleSp}sp is not above detail ${NotificationBarStyle.DetailSp}sp",
            NotificationBarStyle.TitleSp > NotificationBarStyle.DetailSp,
        )
    }
}
