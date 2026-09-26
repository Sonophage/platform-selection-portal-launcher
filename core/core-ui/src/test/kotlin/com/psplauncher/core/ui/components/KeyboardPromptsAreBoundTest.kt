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
        ControllerIcon.FACE_WEST to KeyEvent.KEYCODE_F2,
        ControllerIcon.FACE_NORTH to KeyEvent.KEYCODE_F3,
        ControllerIcon.SELECT to KeyEvent.KEYCODE_TAB,
        ControllerIcon.BUMPER_LEFT to KeyEvent.KEYCODE_PAGE_UP,
        ControllerIcon.BUMPER_RIGHT to KeyEvent.KEYCODE_PAGE_DOWN,
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

    @Test
    fun `no default binding claims a key that types a character`() {
        // The constraint that makes type-to-search possible at all, and the one that was broken:
        // GamepadInputHandler claims a bound keycode before any text field sees it, so a letter
        // bound to an action is a letter that can never be typed — into the app drawer's search
        // box, a rename dialog, or the search these bindings exist to open. Q, E, Space and Shift
        // were bound for exactly one commit and took four characters away from every field.
        //
        // The printable range is checked directly rather than by listing the offenders, so a
        // binding added later is caught whether or not anyone remembers this.
        val printable = DEFAULT_BINDINGS.filter { it.keyCode in TYPES_A_CHARACTER }
        assertTrue(
            "these bindings swallow a character key: ${printable.map { it.keyCode }}",
            printable.isEmpty(),
        )
    }

    private companion object {
        /** Letters, digits, space and the punctuation keys — everything that produces text. */
        val TYPES_A_CHARACTER: Set<Int> =
            (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9).toSet() +
                (KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z).toSet() +
                setOf(
                    KeyEvent.KEYCODE_SPACE,
                    KeyEvent.KEYCODE_COMMA,
                    KeyEvent.KEYCODE_PERIOD,
                    KeyEvent.KEYCODE_MINUS,
                    KeyEvent.KEYCODE_EQUALS,
                    KeyEvent.KEYCODE_APOSTROPHE,
                    KeyEvent.KEYCODE_SEMICOLON,
                    KeyEvent.KEYCODE_SLASH,
                    KeyEvent.KEYCODE_BACKSLASH,
                    KeyEvent.KEYCODE_LEFT_BRACKET,
                    KeyEvent.KEYCODE_RIGHT_BRACKET,
                    KeyEvent.KEYCODE_GRAVE,
                )
    }
}
