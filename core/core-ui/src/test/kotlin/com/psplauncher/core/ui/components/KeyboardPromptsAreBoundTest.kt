package com.psplauncher.core.ui.components

import android.view.KeyEvent
import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.DEFAULT_BINDINGS
import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardPromptsAreBoundTest {
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
        val keys = DEFAULT_BINDINGS.map { it.keyCode }
        assertTrue("a keycode is bound twice: $keys", keys.size == keys.toSet().size)
    }

    @Test
    fun `no default binding claims a key that types a character`() {
        val printable = DEFAULT_BINDINGS.filter { it.keyCode in TYPES_A_CHARACTER }
        assertTrue(
            "these bindings swallow a character key: ${printable.map { it.keyCode }}",
            printable.isEmpty(),
        )
    }

    private companion object {
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
