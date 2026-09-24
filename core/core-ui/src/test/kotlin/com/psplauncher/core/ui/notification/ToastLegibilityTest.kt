package com.psplauncher.core.ui.notification

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Holds the toast card to the redesign's own legibility floor.
 *
 * The bundle states one rule for the whole system — "No text below 28 px at native res", for a
 * 1080p handheld held at 30-40cm — and the pill broke it for as long as it existed: a 10sp title
 * and a 9sp message are 23px and 21px on this 2.3375 panel. Nothing failed, nothing looked
 * obviously wrong in a screenshot on a 27" monitor, and the text was a third too small in the
 * hand. That is the failure this exists to catch, and a screenshot cannot catch it.
 *
 * It guards the CONVERSION as much as the numbers. Every figure in the design is a pixel count on
 * a 1920x1080 frame and every figure in the card is a dp, so there is a division between them that
 * has to be right; a test that only compared sp to sp would pass with the density wrong.
 */
class ToastLegibilityTest {

    @Test
    fun `no text in the toast falls below the design's floor`() {
        val floor = ToastStyle.LegibilityFloorPx
        val title = ToastStyle.TitleSize.value * ToastStyle.PanelDensity
        val message = ToastStyle.MessageSize.value * ToastStyle.PanelDensity

        assertTrue(
            "the toast title renders at ${title}px, under the ${floor}px floor",
            title >= floor,
        )
        assertTrue(
            "the toast message renders at ${message}px, under the ${floor}px floor",
            message >= floor,
        )
    }

    @Test
    fun `the title is still bigger than the message`() {
        // The floor pushes the message UP, and the message is the line closest to it — 26px in the
        // mock, raised to the floor here. Raise the floor far enough and it would meet the title,
        // at which point the card has one type size and no hierarchy, and every assertion above
        // would still pass.
        assertTrue(
            "title ${ToastStyle.TitleSize.value}sp is not above message ${ToastStyle.MessageSize.value}sp",
            ToastStyle.TitleSize.value > ToastStyle.MessageSize.value,
        )
    }
}
