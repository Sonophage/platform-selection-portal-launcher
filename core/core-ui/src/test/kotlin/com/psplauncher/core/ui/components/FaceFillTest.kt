package com.psplauncher.core.ui.components

import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The colour a face button is FILLED with.
 *
 * Xbox and PlayStation both colour their four face buttons and it is how people find them — green
 * is where confirm lives on an Xbox pad whatever letter is on it. Resolved by POSITION, like the
 * art, so an X/Y swap moves the colour and the picture together rather than leaving a green button
 * labelled with the wrong action.
 */
class FaceFillTest {

    private val faces = listOf(
        ControllerIcon.FACE_SOUTH,
        ControllerIcon.FACE_EAST,
        ControllerIcon.FACE_WEST,
        ControllerIcon.FACE_NORTH,
    )

    @Test
    fun `Xbox and PlayStation colour all four faces, and each one differently`() {
        listOf(ControllerDisplayType.XBOX, ControllerDisplayType.PLAYSTATION).forEach { family ->
            val tints = faces.map { it.faceFillFor(family) }
            tints.forEachIndexed { i, tint -> assertNotNull("$family has no tint for ${faces[i]}", tint) }
            assertEquals("$family uses one colour twice", faces.size, tints.toSet().size)
        }
    }

    @Test
    fun `Nintendo is left alone, because a Switch pad prints no colours to follow`() {
        // Colouring them would be inventing a convention rather than following one.
        faces.forEach { assertNull(it.faceFillFor(ControllerDisplayType.NINTENDO)) }
    }

    @Test
    fun `nothing but a face position is tinted`() {
        // A coloured bumper or d-pad is not a thing either pad does, and a fully tinted set reads
        // as decoration rather than as hardware.
        val notFaces = ControllerIcon.entries.filterNot { it in faces }
        ControllerDisplayType.entries.forEach { family ->
            notFaces.forEach { icon ->
                assertNull("$family tints $icon, which is not a face position", icon.faceFillFor(family))
            }
        }
    }

    @Test
    fun `the label-only families tint nothing, having nothing to tint`() {
        listOf(ControllerDisplayType.KEYBOARD, ControllerDisplayType.TOUCH).forEach { family ->
            faces.forEach { assertNull(it.faceFillFor(family)) }
        }
    }
}
