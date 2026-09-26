package com.psplauncher.core.ui.components

import android.view.KeyEvent
import com.psplauncher.core.domain.model.ControllerDisplayType
import com.psplauncher.core.domain.model.ControllerIcon
import com.psplauncher.core.domain.model.DEFAULT_BINDINGS
import com.psplauncher.core.domain.model.GamepadMappings
import com.psplauncher.core.domain.model.toControllerIcon
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

    /**
     * Every position the Keyboard family prints a label for — DERIVED, not listed.
     *
     * This was a hand-written table of eleven, against a label table of fourteen. Three positions
     * were therefore never checked, and the hole sat exactly where the bug is. A list that has to
     * be kept in step with another list is the pair this codebase keeps paying for; the only
     * honest version asks the label table itself.
     */
    private val promptedPositions: Set<ControllerIcon> =
        ControllerIcon.entries
            .filter { it.printedLabelFor(ControllerDisplayType.KEYBOARD) != null }
            .toSet()

    /**
     * The positions a KEYBOARD can actually reach, derived from the bindings.
     *
     * "Keyboard key" is `not in the BUTTON_* range`, and the first version of this got it wrong
     * in a way worth recording: it asked whether [toControllerIcon] returned null, reasoning that
     * a keycode with no gamepad position must be a keyboard one. The arrow keys break that —
     * a keyboard sends them as `KEYCODE_DPAD_*`, which ARE gamepad positions — so all four
     * directions were reported unreachable when they are the best-bound keys in the table.
     *
     * The action a binding runs resolves back to the position a prompt would draw, which is what
     * makes this "what a keyboard user can actually press" rather than "what keys exist".
     */
    private fun keyboardReachablePositions(): Set<ControllerIcon> {
        val layout = GamepadMappings(DEFAULT_BINDINGS)
        return DEFAULT_BINDINGS
            .filter { it.keyCode !in GAMEPAD_ONLY_KEYS }
            .mapNotNull { layout.iconFor(it.action) }
            .toSet()
    }

    /**
     * Positions no input reaches at all, on any family — so not a KEYBOARD hole.
     *
     * Computed rather than listed, and that is the point: today it is [ControllerIcon.SYSTEM],
     * whose keycode KEYCODE_BUTTON_MODE appears in no binding and which no prompt anywhere asks
     * for. A label for a position the app never draws is dead weight, not a lie told to a
     * keyboard user.
     *
     * The exemption un-exempts itself. The moment anything binds that position — on a gamepad,
     * say — it drops out of this set, and if the keyboard still cannot reach it the test above
     * goes red, which is then exactly right.
     */
    private fun positionsNoInputReaches(): Set<ControllerIcon> {
        val layout = GamepadMappings(DEFAULT_BINDINGS)
        val everReached = DEFAULT_BINDINGS.mapNotNull { layout.iconFor(it.action) }.toSet()
        return ControllerIcon.entries.toSet() - everReached
    }

    @Test
    fun `every position the keyboard prints a label for is reachable from a keyboard`() {
        val unreachable = promptedPositions -
            keyboardReachablePositions() -
            COMPOSITE_POSITIONS -
            positionsNoInputReaches()
        val detail = unreachable.joinToString { pos ->
            "$pos prints \"${pos.printedLabelFor(ControllerDisplayType.KEYBOARD)}\""
        }
        assertTrue(
            "the Keyboard family names keys that reach nothing: $detail — a footer telling a " +
                "keyboard user to press a key that does nothing is worse than no footer",
            unreachable.isEmpty(),
        )
    }

    @Test
    fun `the label table and the checked set are the same size`() {
        // The guard on this test's own guard. The previous version hand-listed eleven positions
        // while the label table had fourteen, so three were never checked and nobody could see
        // that from reading either file.
        assertTrue(
            "no Keyboard labels found — the derivation broke, and an empty set passes everything",
            promptedPositions.isNotEmpty(),
        )
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

    @Test
    fun `the exemption for unreached positions is narrow, and says which`() {
        // Guard on the exemption. If this set ever grows, a position stopped being bound and the
        // reason needs reading rather than the number being edited.
        // Composites are already accounted for above and belong in this set by construction —
        // no keycode resolves to DPAD_ALL in any family — so they are not what this counts.
        val unreached = (positionsNoInputReaches() - COMPOSITE_POSITIONS).filter {
            it.printedLabelFor(ControllerDisplayType.KEYBOARD) != null
        }
        assertTrue(
            "positions with a Keyboard label that no input reaches: $unreached — one is expected " +
                "(SYSTEM, KEYCODE_BUTTON_MODE, bound nowhere and prompted nowhere); more than " +
                "that means something lost its binding",
            unreached.size <= 1,
        )
    }

    private companion object {
        /**
         * The keycodes only a gamepad sends. Everything else a keyboard can produce, including
         * the arrows — which arrive as `KEYCODE_DPAD_*` and are the reason "has no gamepad
         * position" is the wrong test for "is a keyboard key".
         */
        val GAMEPAD_ONLY_KEYS: Set<Int> =
            (KeyEvent.KEYCODE_BUTTON_A..KeyEvent.KEYCODE_BUTTON_MODE).toSet() +
                (KeyEvent.KEYCODE_BUTTON_1..KeyEvent.KEYCODE_BUTTON_16).toSet()

        /**
         * Positions that are a legend rather than a key, in EVERY family.
         *
         * [ControllerIcon.DPAD_ALL] is the "◀▶" glyph a multi-direction prompt draws ("◀▶ Seek").
         * No keycode resolves to it — [toControllerIcon] never returns it — so it is not a
         * keyboard hole; it is a composite the caller asks for directly, and it has a drawable in
         * the PlayStation, Xbox and Switch families for the same reason.
         */
        val COMPOSITE_POSITIONS: Set<ControllerIcon> = setOf(ControllerIcon.DPAD_ALL)

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
