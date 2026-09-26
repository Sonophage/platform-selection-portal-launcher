package com.psplauncher.core.ui.components

import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.displayLabel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphFamilyCoverageTest {
    private val prompted = listOf(
        ControllerIcon.FACE_SOUTH,
        ControllerIcon.FACE_EAST,
        ControllerIcon.DPAD_LEFT,
        ControllerIcon.DPAD_RIGHT,
        ControllerIcon.DPAD_UP,
        ControllerIcon.DPAD_DOWN,
    )

    @Test
    fun `every family can render every input the launcher prompts with`() {
        ControllerDisplayType.entries.forEach { family ->
            prompted.forEach { icon ->
                val art = icon.drawableForOrNull(family)
                val label = icon.printedLabelFor(family)
                assertTrue(
                    "${family.displayLabel()} can draw neither art nor a label for $icon",
                    art != null || label != null,
                )
            }
        }
    }

    @Test
    fun `keyboard and touch carry no art at all, which is the point of them`() {
        listOf(ControllerDisplayType.KEYBOARD, ControllerDisplayType.TOUCH).forEach { family ->
            ControllerIcon.entries.forEach { icon ->
                assertNull("${family.displayLabel()} has art for $icon", icon.drawableForOrNull(family))
            }
        }
    }

    @Test
    fun `touch names gestures, never buttons`() {
        assertEquals("Tap", ControllerIcon.FACE_SOUTH.printedLabelFor(ControllerDisplayType.TOUCH))
        assertEquals("Back", ControllerIcon.FACE_EAST.printedLabelFor(ControllerDisplayType.TOUCH))
    }

    @Test
    fun `touch leaves the shoulders absent rather than inventing a gesture for them`() {
        assertNull(ControllerIcon.BUMPER_LEFT.printedLabelFor(ControllerDisplayType.TOUCH))
        assertNull(ControllerIcon.BUMPER_RIGHT.printedLabelFor(ControllerDisplayType.TOUCH))
    }

    @Test
    fun `keyboard confirms with Enter and backs out with Escape`() {
        assertEquals("Enter", ControllerIcon.FACE_SOUTH.printedLabelFor(ControllerDisplayType.KEYBOARD))
        assertEquals("Esc", ControllerIcon.FACE_EAST.printedLabelFor(ControllerDisplayType.KEYBOARD))
    }

    @Test
    fun `every family has a name for the settings picker`() {
        ControllerDisplayType.entries.forEach { assertNotNull(it.displayLabel()) }
    }

    private fun assertEquals(expected: String?, actual: String?) =
        org.junit.Assert.assertEquals(expected, actual)
}
