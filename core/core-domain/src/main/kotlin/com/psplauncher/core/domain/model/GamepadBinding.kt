package com.psplauncher.core.domain.model

import android.view.KeyEvent
import kotlinx.serialization.Serializable

@Serializable
data class GamepadBinding(
    val keyCode: Int,
    val action: GamepadAction,
)

@Serializable
data class GamepadMappings(
    val bindings: List<GamepadBinding> = DEFAULT_BINDINGS,
) {
    fun actionFor(keyCode: Int): GamepadAction? =
        bindings.firstOrNull { it.keyCode == keyCode }?.action

    /**
     * The physical button that currently performs [action], for UI prompts.
     *
     * The inverse of [actionFor], and the reason footers cannot drift from the
     * pad: chrome asks for the action it names, not for a fixed position.
     *
     * Keycodes with no position on a gamepad — Enter, hardware Back, D-pad
     * centre — are skipped. They are legitimate ways to drive the UI but not
     * buttons a prompt can point at, and several share an action with a real
     * face button (SELECT is bound three times), so taking the first binding
     * blindly would show a D-pad glyph for "Launch".
     */
    fun iconFor(action: GamepadAction): ControllerIcon? =
        bindings.asSequence()
            .filter { it.action == action }
            .mapNotNull { it.keyCode.toControllerIcon() }
            .firstOrNull()

    /**
     * The positions performing [actions], in the order asked for, for a prompt
     * that names more than one input at once ("◀▶ Seek", "L1/R1 Prev / Next").
     *
     * Unbound actions drop out, as they do for [iconFor]. Repeats collapse:
     * under a swapped layout two listed actions can land on the same button, and
     * the same glyph drawn twice reads as a broken prompt rather than a pair.
     */
    fun iconsFor(actions: List<GamepadAction>): List<ControllerIcon> =
        actions.mapNotNull { iconFor(it) }.distinct()
}

val DEFAULT_BINDINGS = listOf(
    GamepadBinding(KeyEvent.KEYCODE_BUTTON_A,      GamepadAction.SELECT),
    GamepadBinding(KeyEvent.KEYCODE_BUTTON_B,      GamepadAction.BACK),
    GamepadBinding(KeyEvent.KEYCODE_BUTTON_X,      GamepadAction.CHANGE_SORT),
    GamepadBinding(KeyEvent.KEYCODE_BUTTON_Y,      GamepadAction.OPEN_CONTEXT_MENU),
    GamepadBinding(KeyEvent.KEYCODE_DPAD_UP,       GamepadAction.NAVIGATE_UP),
    GamepadBinding(KeyEvent.KEYCODE_DPAD_DOWN,     GamepadAction.NAVIGATE_DOWN),
    GamepadBinding(KeyEvent.KEYCODE_DPAD_LEFT,     GamepadAction.NAVIGATE_LEFT),
    GamepadBinding(KeyEvent.KEYCODE_DPAD_RIGHT,    GamepadAction.NAVIGATE_RIGHT),
    GamepadBinding(KeyEvent.KEYCODE_BUTTON_L1,     GamepadAction.PREV_CATEGORY),
    GamepadBinding(KeyEvent.KEYCODE_BUTTON_R1,     GamepadAction.NEXT_CATEGORY),
    GamepadBinding(KeyEvent.KEYCODE_BUTTON_START,  GamepadAction.HOME),
    // Select opens search. It used to be a second button for sort, which X already does -- a
    // leftover from the removed task tray rather than a choice. Search is what the other button
    // on a handheld has always been for, and a face button could not be spared for it.
    GamepadBinding(KeyEvent.KEYCODE_BUTTON_SELECT, GamepadAction.OPEN_SEARCH),
    GamepadBinding(KeyEvent.KEYCODE_ENTER,         GamepadAction.SELECT),
    GamepadBinding(KeyEvent.KEYCODE_BACK,          GamepadAction.BACK),
    GamepadBinding(KeyEvent.KEYCODE_DPAD_CENTER,   GamepadAction.SELECT),

    // ── Keyboard ─────────────────────────────────────────────────────────────
    //
    // A keyboard already drove most of this by accident: Android sends arrow keys as DPAD_* and
    // Enter was bound above. The rest were not, and that became a LIE the moment the Keyboard
    // glyph family shipped — its prompts say Esc backs out, Tab searches, Space opens options, and
    // none of those keys reached the launcher. A footer that names a key which does nothing is
    // worse than no footer.
    //
    // NOT A SINGLE CHARACTER KEY among them, and that is the whole constraint.
    //
    // GamepadInputHandler consumes any keycode it has a binding for, before anything else sees it
    // and without asking whether a text field has focus. So a letter bound here is a letter that
    // can never be TYPED — into the app drawer's search box, a rename dialog, or the search this
    // very list is meant to open. The first pass at this bound Q, E, Space and Shift and quietly
    // took four characters away from every text field in the app.
    //
    // Escape, Tab, the function row and the page keys all arrive with no printable character, so
    // they can be buttons without costing anyone a keystroke. Escape first: backing out is what a
    // keyboard user reaches for before anything else, and the alternative was BACK, which is a
    // phone button and not on a keyboard at all.
    GamepadBinding(KeyEvent.KEYCODE_ESCAPE,        GamepadAction.BACK),
    GamepadBinding(KeyEvent.KEYCODE_TAB,           GamepadAction.OPEN_SEARCH),
    GamepadBinding(KeyEvent.KEYCODE_F2,            GamepadAction.CHANGE_SORT),
    GamepadBinding(KeyEvent.KEYCODE_F3,            GamepadAction.OPEN_CONTEXT_MENU),
    GamepadBinding(KeyEvent.KEYCODE_PAGE_UP,       GamepadAction.PREV_CATEGORY),
    GamepadBinding(KeyEvent.KEYCODE_PAGE_DOWN,     GamepadAction.NEXT_CATEGORY),
)

/**
 * The full binding table for a pair of layout preferences.
 *
 * Rebuilt from [DEFAULT_BINDINGS] rather than mutated in place, so toggling a
 * setting back and forth always lands on a clean state and every non-face
 * binding survives — including the Enter / D-pad-centre / hardware-Back aliases
 * an earlier per-action remap path used to strip.
 *
 * `gamepadMappingsFor(STANDARD, STANDARD)` is by construction equal to the
 * defaults; a divergence there once meant a button changed meaning the first
 * time the user opened controller settings.
 */
fun gamepadMappingsFor(
    confirmBack: ConfirmBackLayout,
    xy: XYLayout,
): GamepadMappings {
    val confirmKey = when (confirmBack) {
        ConfirmBackLayout.STANDARD -> KeyEvent.KEYCODE_BUTTON_A
        ConfirmBackLayout.REVERSED -> KeyEvent.KEYCODE_BUTTON_B
    }
    val contextMenuKey = when (xy) {
        XYLayout.STANDARD -> KeyEvent.KEYCODE_BUTTON_Y
        XYLayout.SWAPPED -> KeyEvent.KEYCODE_BUTTON_X
    }
    return GamepadMappings(
        DEFAULT_BINDINGS.map { binding ->
            when (binding.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B -> GamepadBinding(
                    binding.keyCode,
                    if (binding.keyCode == confirmKey) GamepadAction.SELECT else GamepadAction.BACK,
                )
                KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y -> GamepadBinding(
                    binding.keyCode,
                    if (binding.keyCode == contextMenuKey) {
                        GamepadAction.OPEN_CONTEXT_MENU
                    } else {
                        GamepadAction.CHANGE_SORT
                    },
                )
                else -> binding
            }
        },
    )
}

fun GamepadAction.displayLabel(): String = when (this) {
    GamepadAction.NAVIGATE_UP       -> "Navigate Up"
    GamepadAction.NAVIGATE_DOWN     -> "Navigate Down"
    GamepadAction.NAVIGATE_LEFT     -> "Navigate Left (Previous Category)"
    GamepadAction.NAVIGATE_RIGHT    -> "Navigate Right (Next Category)"
    GamepadAction.SELECT            -> "Select / Launch"
    GamepadAction.BACK              -> "Back / Close"
    GamepadAction.OPEN_CONTEXT_MENU -> "Options / Context Menu"
    GamepadAction.CHANGE_SORT       -> "Change Sort Order"
    GamepadAction.OPEN_SEARCH       -> "Search Your Libraries"
    GamepadAction.PREV_CATEGORY     -> "Previous Tab (App Drawer)"
    GamepadAction.NEXT_CATEGORY     -> "Next Tab (App Drawer)"
    GamepadAction.HOME              -> "Start (Confirm in pickers)"
}

fun Int.keycodeDisplayName(): String = when (this) {
    KeyEvent.KEYCODE_BUTTON_A      -> "A / Cross"
    KeyEvent.KEYCODE_BUTTON_B      -> "B / Circle"
    KeyEvent.KEYCODE_BUTTON_X      -> "X / Square"
    KeyEvent.KEYCODE_BUTTON_Y      -> "Y / Triangle"
    KeyEvent.KEYCODE_DPAD_UP       -> "D-Pad Up"
    KeyEvent.KEYCODE_DPAD_DOWN     -> "D-Pad Down"
    KeyEvent.KEYCODE_DPAD_LEFT     -> "D-Pad Left"
    KeyEvent.KEYCODE_DPAD_RIGHT    -> "D-Pad Right"
    KeyEvent.KEYCODE_DPAD_CENTER   -> "D-Pad Center"
    KeyEvent.KEYCODE_BUTTON_L1     -> "L1"
    KeyEvent.KEYCODE_BUTTON_R1     -> "R1"
    KeyEvent.KEYCODE_BUTTON_L2     -> "L2"
    KeyEvent.KEYCODE_BUTTON_R2     -> "R2"
    KeyEvent.KEYCODE_BUTTON_START  -> "Start"
    KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
    KeyEvent.KEYCODE_ENTER         -> "Enter"
    KeyEvent.KEYCODE_BACK          -> "Back"
    else                           -> "Key $this"
}
