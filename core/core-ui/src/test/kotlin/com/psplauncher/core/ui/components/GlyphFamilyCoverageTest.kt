package com.psplauncher.core.ui.components

import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.displayLabel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every glyph family can draw the inputs the launcher actually prompts with.
 *
 * A family resolves to art first and a printed label second, and BOTH may be absent — that is how
 * a DualSense touchpad degrades on an Xbox pad, and it is correct. It stops being correct for an
 * input the app names on its own footers: there the prompt renders nothing at all, which looks
 * like a missing icon and is really a missing row in a table nobody re-reads.
 *
 * Keyboard and Touch have no art by design and exist entirely on that second path, so this is the
 * only thing standing between them and a bar of bare words with no buttons in front of them.
 */
class GlyphFamilyCoverageTest {

    /** What the launcher's own prompts name: confirm, back, the menu buttons, the directions. */
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
        // They exist to cost two tables and no icon pack. A drawable appearing here means someone
        // has started shipping a fourth and fifth pack without saying so.
        listOf(ControllerDisplayType.KEYBOARD, ControllerDisplayType.TOUCH).forEach { family ->
            ControllerIcon.entries.forEach { icon ->
                assertNull("${family.displayLabel()} has art for $icon", icon.drawableForOrNull(family))
            }
        }
    }

    @Test
    fun `touch names gestures, never buttons`() {
        // The whole reason it is its own family: someone driving by finger has no face buttons,
        // and a prompt showing them one is naming hardware they are not holding.
        assertEquals("Tap", ControllerIcon.FACE_SOUTH.printedLabelFor(ControllerDisplayType.TOUCH))
        assertEquals("Back", ControllerIcon.FACE_EAST.printedLabelFor(ControllerDisplayType.TOUCH))
    }

    @Test
    fun `touch leaves the shoulders absent rather than inventing a gesture for them`() {
        // There is no finger equivalent of a bumper. Absent renders nothing, which is honest;
        // a made-up word would be an instruction that cannot be followed.
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
