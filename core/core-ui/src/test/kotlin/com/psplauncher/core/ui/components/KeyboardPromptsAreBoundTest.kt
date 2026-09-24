package com.psplauncher.core.ui.components

import android.view.KeyEvent
import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.DEFAULT_BINDINGS
import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every key the Keyboard prompts name is a key the launcher actually answers to.
 *
 * This is the pair that drifts: a glyph table in core-ui says "Esc", a binding table in core-domain
 * decides what Escape does, and nothing joined them. The Keyboard family shipped for one commit
 * naming Shift, Space, Tab, Q and E while not one of those keys reached the launcher — a footer
 * telling a keyboard user to press a key that does nothing, which is worse than no footer.
 *
 * Asserted through the ACTION rather than by matching strings: the prompt names a position, the
 * binding names a keycode, and the thing they have to agree about is that the position is reachable
 * at all.
 */
class KeyboardPromptsAreBoundTest {

    /** Which physical key the Keyboard family tells the user to press for each position. */
    private val promptedKeys = mapOf(
        ControllerIcon.FACE_SOUTH to KeyEvent.KEYCODE_ENTER,
        ControllerIcon.FACE_EAST to KeyEvent.KEYCODE_ESCAPE,
        ControllerIcon.FACE_WEST to KeyEvent.KEYCODE_SHIFT_LEFT,
        ControllerIcon.FACE_NORTH to KeyEvent.KEYCODE_SPACE,
        ControllerIcon.SELECT to KeyEvent.KEYCODE_TAB,
        ControllerIcon.BUMPER_LEFT to KeyEvent.KEYCODE_Q,
        ControllerIcon.BUMPER_RIGHT to KeyEvent.KEYCODE_E,
        ControllerIcon.DPAD_UP to KeyEvent.KEYCODE_DPAD_UP,
        ControllerIcon.DPAD_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
        ControllerIcon.DPAD_LEFT to KeyEvent.KEYCODE_DPAD_LEFT,
        ControllerIcon.DPAD_RIGHT to KeyEvent.KEYCODE_DPAD_RIGHT,
    )

    @Test
    fun `every key the keyboard prompts name is bound to something`() {
        val bound = DEFAULT_BINDINGS.associate { it.keyCode to it.action }
        promptedKeys.forEach { (position, keyCode) ->
            assertNotNull(
                "the Keyboard family prompts $position but keycode $keyCode is bound to nothing",
                bound[keyCode],
            )
        }
    }

    @Test
    fun `every position the keyboard prompts also has a label to print`() {
        // The other half: a bound key with no label renders nothing at all, which looks like a
        // missing icon rather than like a missing row in a table.
        promptedKeys.keys.forEach { position ->
            assertNotNull(
                "$position has no Keyboard label",
                position.printedLabelFor(ControllerDisplayType.KEYBOARD),
            )
        }
    }

    @Test
    fun `Enter confirms and Escape goes back, which is what every desktop means by them`() {
        val bound = DEFAULT_BINDINGS.associate { it.keyCode to it.action }
        assertTrue("Enter must confirm", bound[KeyEvent.KEYCODE_ENTER] == GamepadAction.SELECT)
        assertTrue("Escape must go back", bound[KeyEvent.KEYCODE_ESCAPE] == GamepadAction.BACK)
    }

    @Test
    fun `no two default bindings claim the same key`() {
        // A duplicate makes which action runs depend on list order, which nothing promises.
        val keys = DEFAULT_BINDINGS.map { it.keyCode }
        assertTrue("a keycode is bound twice: $keys", keys.size == keys.toSet().size)
    }
}
