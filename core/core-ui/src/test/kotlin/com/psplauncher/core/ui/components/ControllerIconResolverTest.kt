package com.psplauncher.core.ui.components

import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerIconResolverTest {
    private val ART_FAMILIES = listOf(
        ControllerDisplayType.PLAYSTATION,
        ControllerDisplayType.NINTENDO,
        ControllerDisplayType.XBOX,
    )

    private val LABEL_FAMILIES = listOf(
        ControllerDisplayType.KEYBOARD,
        ControllerDisplayType.TOUCH,
    )

    @Test
    fun `south face is Cross, A, and B respectively`() {
        assertEquals(
            R.drawable.ctl_ps_face_south,
            ControllerIcon.FACE_SOUTH.drawableForOrNull(ControllerDisplayType.PLAYSTATION),
        )
        assertEquals(
            R.drawable.ctl_xb_face_south,
            ControllerIcon.FACE_SOUTH.drawableForOrNull(ControllerDisplayType.XBOX),
        )
        assertEquals(
            R.drawable.ctl_ns_face_south,
            ControllerIcon.FACE_SOUTH.drawableForOrNull(ControllerDisplayType.NINTENDO),
        )
    }

    @Test
    fun `Nintendo mirrors Xbox on both face axes`() {
        assertEquals("A", ControllerIcon.FACE_SOUTH.printedLabelFor(ControllerDisplayType.XBOX))
        assertEquals("B", ControllerIcon.FACE_SOUTH.printedLabelFor(ControllerDisplayType.NINTENDO))
        assertEquals("B", ControllerIcon.FACE_EAST.printedLabelFor(ControllerDisplayType.XBOX))
        assertEquals("A", ControllerIcon.FACE_EAST.printedLabelFor(ControllerDisplayType.NINTENDO))
        assertEquals("X", ControllerIcon.FACE_WEST.printedLabelFor(ControllerDisplayType.XBOX))
        assertEquals("Y", ControllerIcon.FACE_WEST.printedLabelFor(ControllerDisplayType.NINTENDO))
        assertEquals("Y", ControllerIcon.FACE_NORTH.printedLabelFor(ControllerDisplayType.XBOX))
        assertEquals("X", ControllerIcon.FACE_NORTH.printedLabelFor(ControllerDisplayType.NINTENDO))
    }

    @Test
    fun `no family ever borrows another family's art`() {
        val families = ControllerDisplayType.entries
        for (icon in ControllerIcon.entries) {
            val resolved = families.mapNotNull { icon.drawableForOrNull(it) }
            assertEquals(
                "$icon resolves to shared art across families",
                resolved.size,
                resolved.toSet().size,
            )
        }
    }

    @Test
    fun `the four face positions are distinct within every family`() {
        val faces = listOf(
            ControllerIcon.FACE_SOUTH, ControllerIcon.FACE_EAST,
            ControllerIcon.FACE_WEST, ControllerIcon.FACE_NORTH,
        )
        for (family in ART_FAMILIES) {
            val art = faces.map { it.drawableForOrNull(family) }
            assertEquals("$family draws a face position twice", 4, art.toSet().size)
        }

        for (family in LABEL_FAMILIES) {
            val labels = faces.mapNotNull { it.printedLabelFor(family) }
            assertEquals("$family prints a face position twice", labels.size, labels.toSet().size)
        }
    }

    @Test
    fun `command bar positions resolve for every family with art`() {
        val commandBar = listOf(
            ControllerIcon.FACE_SOUTH, ControllerIcon.FACE_EAST,
            ControllerIcon.FACE_WEST, ControllerIcon.FACE_NORTH,
            ControllerIcon.BUMPER_LEFT, ControllerIcon.BUMPER_RIGHT,
        )
        for (family in ART_FAMILIES) {
            for (icon in commandBar) {
                assertNotNull("$icon missing for $family", icon.drawableForOrNull(family))
            }
        }
    }

    @Test
    fun `the label-only families are the ones that ship no art, and nothing else is`() {
        assertEquals(
            ControllerDisplayType.entries.toSet(),
            (ART_FAMILIES + LABEL_FAMILIES).toSet(),
        )
        for (family in ART_FAMILIES) {
            assertNotNull("$family is in ART_FAMILIES with no art", ControllerIcon.FACE_SOUTH.drawableForOrNull(family))
        }
        for (family in LABEL_FAMILIES) {
            assertNull("$family is in LABEL_FAMILIES with art", ControllerIcon.FACE_SOUTH.drawableForOrNull(family))
        }
    }

    @Test
    fun `touchpad is PlayStation-only and returns null elsewhere`() {
        assertNotNull(ControllerIcon.TOUCHPAD.drawableForOrNull(ControllerDisplayType.PLAYSTATION))
        assertNotNull(ControllerIcon.TOUCHPAD_LEFT.drawableForOrNull(ControllerDisplayType.PLAYSTATION))
        assertNotNull(ControllerIcon.TOUCHPAD_RIGHT.drawableForOrNull(ControllerDisplayType.PLAYSTATION))
        assertNull(ControllerIcon.TOUCHPAD.drawableForOrNull(ControllerDisplayType.XBOX))
        assertNull(ControllerIcon.TOUCHPAD.drawableForOrNull(ControllerDisplayType.NINTENDO))
    }

    @Test
    fun `Switch-exclusive inputs return null on the other families`() {
        val switchOnly = listOf(
            ControllerIcon.GAME_CHAT, ControllerIcon.CAMERA,
            ControllerIcon.PADDLE_LEFT, ControllerIcon.PADDLE_RIGHT,
            ControllerIcon.JOYCON_SL, ControllerIcon.JOYCON_SR,
        )
        for (icon in switchOnly) {
            assertNotNull("$icon missing for Nintendo", icon.drawableForOrNull(ControllerDisplayType.NINTENDO))
            assertNull(icon.drawableForOrNull(ControllerDisplayType.PLAYSTATION))
            assertNull(icon.drawableForOrNull(ControllerDisplayType.XBOX))
        }
    }

    @Test
    fun `every icon that resolves to art also has a printed label or is stick-or-dpad art`() {
        val unlettered = setOf(
            ControllerIcon.DPAD_UP, ControllerIcon.DPAD_DOWN, ControllerIcon.DPAD_LEFT,
            ControllerIcon.DPAD_RIGHT, ControllerIcon.DPAD_ALL,
            ControllerIcon.STICK_LEFT, ControllerIcon.STICK_RIGHT,
        )
        for (family in ControllerDisplayType.entries) {
            for (icon in ControllerIcon.entries) {
                if (icon.drawableForOrNull(family) == null || icon in unlettered) continue
                assertNotNull("$icon has art but no label for $family", icon.printedLabelFor(family))
            }
        }
    }
}
