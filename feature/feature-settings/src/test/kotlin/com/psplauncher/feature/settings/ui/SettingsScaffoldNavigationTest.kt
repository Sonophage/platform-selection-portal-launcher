package com.psplauncher.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.ui.theme.PFPTheme
import kotlinx.coroutines.channels.Channel
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real SettingsScaffold with controller actions (the same CompositionLocal channel
 * SettingsNavHost uses) and asserts the ordered focus sequence: section headers are skipped,
 * the cursor walks action row -> read-only row -> text field, SELECT dispatches through
 * ControllerNavigationState, and UP at the first item clamps in place.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class SettingsScaffoldNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val pendingAction = mutableStateOf<GamepadAction?>(null)
    // Actions are pumped through a channel consumed by a composition coroutine: state writes
    // made inside the composition are the only ones the test recomposer observes reliably.
    private val actions = Channel<GamepadAction>(Channel.UNLIMITED)
    // Plain var (not snapshot state): the waitUntil condition reads it from the test thread, and
    // snapshot reads there may not observe composition-side writes.
    private var consumedPlain = false

    private fun showScreen(
        onBack: () -> Unit = {},
        leftBacksOut: Boolean = true,
        // A real catalog screen id, which is what gives the scaffold a section rail to step into.
        // Null (the default) means no rail, which is how every other test here wants it.
        screenId: String? = null,
        body: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            PFPTheme {
                LaunchedEffect(Unit) {
                    for (action in actions) pendingAction.value = action
                }
                // Standing in for SettingsNavHost, which is where the overlay slot really lives:
                // a screen's prompts are siblings of its scaffold, so the slot has to be provided
                // above both of them.
                val overlaySlot = remember { mutableStateOf<((GamepadAction) -> Unit)?>(null) }
                CompositionLocalProvider(
                    LocalSettingsOverlayInput provides overlaySlot,
                    LocalSettingsPendingAction provides pendingAction.value,
                    LocalSettingsActionConsumed provides { consumedPlain = true },
                    LocalSettingsLeftBacksOut provides leftBacksOut,
                    LocalSettingsScreenId provides screenId,
                ) {
                    SettingsScaffold(
                        title = "Settings",
                        subtitle = "Test screen",
                        onBack = onBack,
                    ) {
                        body()
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    /** Drives one controller action through the scaffold and waits until it is consumed. */
    private fun press(action: GamepadAction) {
        actions.trySend(action)
        composeRule.waitUntil(10_000) { consumedPlain }
        consumedPlain = false
        pendingAction.value = null
        composeRule.waitForIdle()
    }

    /** Asserts the single focused node carries [text] (directly or via a descendant). */
    private fun assertFocusedRow(text: String) {
        composeRule.onNode(isFocused())
            .assert(hasText(text) or hasAnyDescendant(hasText(text)))
    }

    /**
     * Reports what the content believes about its own cursor.
     *
     * LocalSettingsCursorVisible is the single flag every content row consults before drawing the
     * focus plate, so reading it here is reading the thing under test rather than a proxy for it.
     */
    @Composable
    private fun ContentCursorProbe(report: (Boolean) -> Unit) {
        val visible = LocalSettingsCursorVisible.current
        SideEffect { report(visible) }
    }

    @Test
    fun `stepping into the rail stands the content cursor down`() {
        // Measured on the tablet: Settings, Library Manager, one press of LEFT put the cursor in
        // the rail and left BOTH the rail row and the content row wearing the same filled plate.
        // Two identical cursors, and nothing on screen saying which one the D-pad moved.
        var contentCursor: Boolean? = null
        showScreen(screenId = "settings_library") {
            SettingsRow(label = "Add ROM Root", onClick = {})
            ContentCursorProbe { contentCursor = it }
        }

        assertEquals("the content owns the cursor when the screen opens", true, contentCursor)
        assertFocusedRow("Add ROM Root")

        press(GamepadAction.NAVIGATE_LEFT)
        assertEquals("LEFT into the rail must stand the content cursor down", false, contentCursor)

        // Coming back restores it, and to the same row -- the content never lost Compose focus,
        // only the drawing stood down. (The help band is unaffected by design: it reads the
        // scaffold's own cursorVisible state, not the value provided here.)
        press(GamepadAction.NAVIGATE_RIGHT)
        assertEquals("RIGHT must give the cursor back to the content", true, contentCursor)
        assertFocusedRow("Add ROM Root")
    }

    @Test
    fun `a screen with no rail keeps its cursor when LEFT backs out`() {
        // The control. Without a rail there is nothing to hand the cursor to, so the flag must
        // not move -- otherwise the test above would pass on any screen that merely handled LEFT.
        var contentCursor: Boolean? = null
        var backs = 0
        showScreen(onBack = { backs++ }) {
            SettingsRow(label = "Add ROM Root", onClick = {})
            ContentCursorProbe { contentCursor = it }
        }

        press(GamepadAction.NAVIGATE_LEFT)
        assertEquals("LEFT with no rail backs out of the screen", 1, backs)
        assertEquals("and the content keeps its cursor", true, contentCursor)
    }

    @Test
    fun `an overlay takes every press while it is open`() {
        // A prompt drawn over a settings screen has a live list underneath it. Nothing may move
        // down there while the prompt is up, or DOWN walks the hidden list and A fires whatever
        // row it landed on -- which is how a confirmation ends up performing something else.
        val seen = mutableListOf<GamepadAction>()
        val overlayOpen = mutableStateOf(false)
        showScreen {
            SettingsRow(label = "Theme", onClick = {})
            SettingsRow(label = "Sound", onClick = {})
            if (overlayOpen.value) SettingsOverlayInput { seen += it }
        }

        assertFocusedRow("Theme")
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Sound")

        composeRule.runOnIdle { overlayOpen.value = true }
        composeRule.waitForIdle()
        press(GamepadAction.NAVIGATE_DOWN)
        press(GamepadAction.SELECT)

        assertEquals(
            "the overlay must receive the presses",
            listOf(GamepadAction.NAVIGATE_DOWN, GamepadAction.SELECT),
            seen,
        )
        assertFocusedRow("Sound")

        // And it gives the pad back when it leaves, rather than keeping it forever.
        composeRule.runOnIdle { overlayOpen.value = false }
        composeRule.waitForIdle()
        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("Theme")
        assertEquals("a closed overlay must not keep receiving presses", 2, seen.size)
    }

    @Test
    fun `controller focus lands on headers, rows and text fields in order`() {
        var themeSelects = 0
        var deleteSelects = 0
        var backCount = 0
        showScreen(onBack = { backCount++ }) {
            SettingsGroup("Appearance")
            SettingsRow(
                label = "Theme",
                onClick = { themeSelects++ },
                actions = listOf(
                    SettingsRowAction(label = "Delete theme", onClick = { deleteSelects++ }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete theme")
                    },
                ),
            )
            SettingsValueRow(label = "Version", value = "1.0")
            SettingsTextFieldRow(label = "Folder", value = "/roms", onValueChange = {})
        }

        // Opens with the first actionable row focused — the header is skipped.
        assertFocusedRow("Theme")

        // UP from the first row clamps in place (headers are skippable, so there is nothing
        // above it) and the screen pans to the top.
        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("Theme")

        // DOWN walks action row -> read-only row -> text field, skipping the header.
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Version")
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("/roms")

        // Boundary clamp: DOWN at the last row stays put.
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("/roms")

        // SELECT dispatches through the model to the focused row's action.
        // From the text field: UP -> read-only row, UP -> Theme action row.
        press(GamepadAction.NAVIGATE_UP)
        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("Theme")
        press(GamepadAction.SELECT)
        assertEquals(1, themeSelects)

        // RIGHT reaches the row's inline action; SELECT activates it (not the row action).
        press(GamepadAction.NAVIGATE_RIGHT)
        composeRule.onNode(isFocused()).assert(hasContentDescription("Delete theme"))
        press(GamepadAction.SELECT)
        assertEquals(1, deleteSelects)
        assertEquals(1, themeSelects)

        // LEFT returns to the row.
        press(GamepadAction.NAVIGATE_LEFT)
        assertFocusedRow("Theme")

        // SELECT over a read-only row is a no-op.
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Version")
        press(GamepadAction.SELECT)
        assertEquals(1, themeSelects)

        // BACK exits through the scaffold's back handler.
        press(GamepadAction.BACK)
        assertEquals(1, backCount)
    }

    @Test
    fun `LEFT leaves the screen only where LEFT has nothing else to do`() {
        var backCount = 0
        var deleteSelects = 0
        showScreen(onBack = { backCount++ }) {
            SettingsRow(
                label = "Theme",
                onClick = {},
                actions = listOf(
                    SettingsRowAction(label = "Delete theme", onClick = { deleteSelects++ }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete theme")
                    },
                ),
            )
            SettingsValueRow(label = "Version", value = "1.0")
        }

        // A row WITH inline actions: LEFT still steps into them, and back out of them, as before —
        // the fallthrough must never outrank an existing consumer.
        assertFocusedRow("Theme")
        press(GamepadAction.NAVIGATE_RIGHT)
        composeRule.onNode(isFocused()).assert(hasContentDescription("Delete theme"))
        press(GamepadAction.NAVIGATE_LEFT)
        assertFocusedRow("Theme")
        assertEquals(0, backCount)

        // A row WITHOUT them: LEFT used to be a silent no-op; now it leaves the screen.
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Version")
        press(GamepadAction.NAVIGATE_LEFT)
        assertEquals(1, backCount)
        assertEquals(0, deleteSelects)
    }

    @Test
    fun `with the preference off LEFT is the no-op it always was`() {
        var backCount = 0
        showScreen(onBack = { backCount++ }, leftBacksOut = false) {
            SettingsRow(label = "Theme", onClick = {})
        }

        assertFocusedRow("Theme")
        press(GamepadAction.NAVIGATE_LEFT)
        assertEquals(0, backCount)
        assertFocusedRow("Theme")
    }

    @Test
    fun `rows inserted mid-list keep visual navigation order`() {
        // Mimics a fresh Library Manager open: placeholder rows compose first, then the ROM
        // root path and console cards load in and insert mid-list (async data).
        val roots = mutableStateOf(emptyList<String>())
        val consoles = mutableStateOf(emptyList<String>())
        val load = Channel<Unit>(Channel.UNLIMITED)

        composeRule.setContent {
            PFPTheme {
                LaunchedEffect(Unit) {
                    for (action in actions) pendingAction.value = action
                }
                LaunchedEffect(Unit) {
                    for (u in load) {
                        roots.value = listOf("Phone Storage")
                        consoles.value = listOf("PSP Memory Card", "SNES Memory Card")
                    }
                }
                CompositionLocalProvider(
                    LocalSettingsPendingAction provides pendingAction.value,
                    LocalSettingsActionConsumed provides { consumedPlain = true },
                ) {
                    SettingsScaffold(title = "Settings", subtitle = "Library Manager", onBack = {}) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            SettingsGroup("ROM Root Access")
                            if (roots.value.isEmpty()) {
                                SettingsRow(label = "No ROM roots configured", sublabel = "Add a folder below")
                            } else {
                                roots.value.forEach { SettingsRow(label = it, onClick = {}) }
                            }
                            SettingsRow(label = "Add ROM Root", sublabel = "Grant a root folder", onClick = {})
                            SettingsGroup("Consoles")
                            if (consoles.value.isEmpty()) {
                                Text(
                                    text = "No consoles configured",
                                    color = SettingsSubtext,
                                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
                                )
                            } else {
                                consoles.value.forEach { SettingsRow(label = it, sublabel = "console", onClick = {}) }
                            }
                            SettingsGroup("Manage")
                            SettingsRow(label = "Add Console", onClick = {})
                            SettingsRow(label = "Set Up ROM Folders", onClick = {})
                            SettingsRow(label = "Scan All Consoles", sublabel = "Configure a ROM folder first")
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()

        // Fresh open: Add ROM Root is the first actionable row.
        assertFocusedRow("Add ROM Root")

        // The root path and console cards load in, inserting rows mid-list.
        load.trySend(Unit)
        composeRule.waitForIdle()

        // DOWN walks the VISUAL order — the inserted rows sit where they belong, so the cursor
        // goes Add ROM Root -> PSP -> SNES -> Add Console (the Manage rows come after the
        // consoles), instead of the stale registration order (Add ROM Root -> Add Console).
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("PSP Memory Card")
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("SNES Memory Card")
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Add Console")
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Set Up ROM Folders")
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Scan All Consoles")
        // Clamped at the last row.
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Scan All Consoles")

        // And UP walks back up through the inserted rows to the root path at the top.
        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("Set Up ROM Folders")
        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("Add Console")
        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("SNES Memory Card")
        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("PSP Memory Card")
        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("Add ROM Root")
        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("Phone Storage")
        // Clamped at the top row.
        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("Phone Storage")
    }

    @Test
    fun `slider node steps with left right after select and back returns to navigation`() {
        var backCount = 0
        var rowSelects = 0
        val volume = mutableStateOf(0.4f)
        showScreen(onBack = { backCount++ }) {
            SettingsRow(label = "Theme", onClick = { rowSelects++ })
            SettingsSliderRow(
                label = "Volume",
                sublabel = "Test volume",
                value = volume.value,
                onValueChange = { volume.value = it },
                valueRange = 0f..1f,
                steps = 4,
                valueFormatter = { "${it}" },
            )
            SettingsRow(label = "Brightness", onClick = {})
        }

        // Sliders are navigable rows but never claim the screen's initial focus.
        assertFocusedRow("Theme")
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Volume")

        // SELECT enters adjust mode: LEFT/RIGHT step the value (0.4 → 0.6 → 0.8 → 0.6) and the
        // cursor stays on the slider row while it is being adjusted.
        press(GamepadAction.SELECT)
        press(GamepadAction.NAVIGATE_RIGHT)
        press(GamepadAction.NAVIGATE_RIGHT)
        press(GamepadAction.NAVIGATE_LEFT)
        assertEquals(0.6f, volume.value, 0.001f)
        assertFocusedRow("Volume")

        // BACK while adjusting only exits adjust mode — it must NOT trigger the screen's back
        // handler (adjusting never pops the settings screen).
        press(GamepadAction.BACK)
        assertEquals(0, backCount)

        // Ordinary row navigation is restored: DOWN walks off the slider to the next row.
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Brightness")

        // And BACK outside adjust mode remains the screen back handler.
        press(GamepadAction.BACK)
        assertEquals(1, backCount)
    }

    @Test
    fun `first dpad press after a touch drag re-anchors to the viewport centre without moving`() {
        // A long, scrollable list so the viewport centre lands mid-list — not on the stale
        // pre-drag row at the top.
        showScreen(onBack = {}) {
            val scrollState = rememberScrollState()
            LocalSettingsScrollStateRegistrar.current(scrollState)
            Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                repeat(30) { i -> SettingsRow(label = "Row ${i + 1}", onClick = {}) }
            }
        }
        // Opens focused on the first row.
        assertFocusedRow("Row 1")

        // Measure the geometry the scaffold re-anchors against, off the content viewport itself
        // rather than off whatever chrome happens to sit under it.
        val rowHeight = composeRule.onNode(isFocused()).fetchSemanticsNode().boundsInRoot.height
        // The scaffold re-anchors on the FULL viewport centre, with no edge margin applied
        // (SettingsScaffold's revivalPress branch: viewportTop + viewportHeight / 2). The old
        // model here measured from the first row's top to a chrome-derived bottom, which is a
        // different midpoint and only agreed by accident.
        val viewportCenter = viewportCenterY()

        // A real touch drag: the contact itself flags the screen as touch-scrolled (the scaffold
        // hides the cursor on any pointer activity); the small move keeps the list from scrolling.
        composeRule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, -5f))
            up()
        }
        composeRule.waitForIdle()

        // Row centres sit half a row below their tops, so compare against centre + rowHeight/2
        // (the scaffold compares row tops against the viewport centre).
        val nearest: Int = (1..30).minByOrNull { i: Int ->
            val centerY = composeRule.onNodeWithText("Row $i")
                .fetchSemanticsNode().boundsInRoot.center.y
            abs(centerY - (viewportCenter + rowHeight / 2f))
        }!!

        // First DOWN is the REVIVAL press: it only makes the cursor reappear on the row
        // nearest the visible centre — it must NOT move. Never the stale pre-drag row (Row 1)
        // or its continuation (Row 2).
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Row $nearest")

        // The second press is normal navigation: exactly one row down from the re-anchored row.
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Row ${(nearest + 1).coerceAtMost(30)}")
    }

    @Test
    fun `cursor never leaves the visible viewport while walking a long list`() {
        // 40 rows, ~14 visible at a time: the cursor must keep itself on screen as it walks.
        showScreen(onBack = {}) {
            val scrollState = rememberScrollState()
            LocalSettingsScrollStateRegistrar.current(scrollState)
            Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                repeat(40) { i -> SettingsRow(label = "Row ${i + 1}", onClick = {}) }
            }
        }
        assertFocusedRow("Row 1")
        // Both edges as the scaffold enforces them: row top >= viewportTop + margin, row bottom
        // <= viewportBottom - margin.
        val contentTop = viewportTop()
        val contentBottom = viewportBottom()
        var rowHeight = 0f
        repeat(30) { step ->
            press(GamepadAction.NAVIGATE_DOWN)
            val bounds = composeRule.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
            if (step == 0) rowHeight = bounds.height
            assertTrue("step $step: row top ${bounds.top} drifted above viewport top $contentTop", bounds.top >= contentTop - 0.5f)
            assertTrue("step $step: row bottom ${bounds.bottom} hit the viewport bottom $contentBottom", bounds.bottom <= contentBottom + 0.5f)
            assertTrue("step $step: row clipped to height ${bounds.height}", abs(bounds.height - rowHeight) < 1f)
        }
    }
    /**
     * The bottom edge the scaffold keeps a focused row above.
     *
     * Read off the tagged content viewport and the production margin, not inferred from the
     * footer prompt's position minus a hand-written 20.dp. That older derivation modelled the
     * chrome rather than the viewport, and its number did not even match CONTENT_EDGE_MARGIN's
     * 16.dp: it passed on the slack between them, and went red the moment the chrome moved.
     */
    private fun viewportBounds() =
        composeRule.onNodeWithTag(SettingsContentViewportTag).fetchSemanticsNode().boundsInRoot

    private fun margin(): Float = with(composeRule.density) { CONTENT_EDGE_MARGIN.toPx() }

    private fun viewportTop(): Float = viewportBounds().top + margin()

    private fun viewportBottom(): Float = viewportBounds().bottom - margin()

    /** The point the revival press re-anchors to: the whole viewport's midpoint, margins aside. */
    private fun viewportCenterY(): Float = viewportBounds().center.y

}
