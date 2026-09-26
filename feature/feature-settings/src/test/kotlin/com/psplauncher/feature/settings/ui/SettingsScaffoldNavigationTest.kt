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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class SettingsScaffoldNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val pendingAction = mutableStateOf<GamepadAction?>(null)

    private val actions = Channel<GamepadAction>(Channel.UNLIMITED)

    private var consumedPlain = false

    private fun showScreen(
        onBack: () -> Unit = {},
        leftBacksOut: Boolean = true,

        screenId: String? = null,
        body: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            PFPTheme {
                LaunchedEffect(Unit) {
                    for (action in actions) pendingAction.value = action
                }

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

    private fun press(action: GamepadAction) {
        actions.trySend(action)
        composeRule.waitUntil(10_000) { consumedPlain }
        consumedPlain = false
        pendingAction.value = null
        composeRule.waitForIdle()
    }

    private fun assertFocusedRow(text: String) {
        composeRule.onNode(isFocused())
            .assert(hasText(text) or hasAnyDescendant(hasText(text)))
    }

    @Composable
    private fun ContentCursorProbe(report: (Boolean) -> Unit) {
        val visible = LocalSettingsCursorVisible.current
        SideEffect { report(visible) }
    }

    @Test
    fun `stepping into the rail stands the content cursor down`() {
        var contentCursor: Boolean? = null
        showScreen(screenId = "settings_library") {
            SettingsRow(label = "Add ROM Root", onClick = {})
            ContentCursorProbe { contentCursor = it }
        }

        assertEquals("the content owns the cursor when the screen opens", true, contentCursor)
        assertFocusedRow("Add ROM Root")

        press(GamepadAction.NAVIGATE_LEFT)
        assertEquals("LEFT into the rail must stand the content cursor down", false, contentCursor)

        press(GamepadAction.NAVIGATE_RIGHT)
        assertEquals("RIGHT must give the cursor back to the content", true, contentCursor)
        assertFocusedRow("Add ROM Root")
    }

    @Test
    fun `a screen with no rail keeps its cursor when LEFT backs out`() {
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

        assertFocusedRow("Theme")

        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("Theme")

        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Version")
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("/roms")

        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("/roms")

        press(GamepadAction.NAVIGATE_UP)
        press(GamepadAction.NAVIGATE_UP)
        assertFocusedRow("Theme")
        press(GamepadAction.SELECT)
        assertEquals(1, themeSelects)

        press(GamepadAction.NAVIGATE_RIGHT)
        composeRule.onNode(isFocused()).assert(hasContentDescription("Delete theme"))
        press(GamepadAction.SELECT)
        assertEquals(1, deleteSelects)
        assertEquals(1, themeSelects)

        press(GamepadAction.NAVIGATE_LEFT)
        assertFocusedRow("Theme")

        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Version")
        press(GamepadAction.SELECT)
        assertEquals(1, themeSelects)

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

        assertFocusedRow("Theme")
        press(GamepadAction.NAVIGATE_RIGHT)
        composeRule.onNode(isFocused()).assert(hasContentDescription("Delete theme"))
        press(GamepadAction.NAVIGATE_LEFT)
        assertFocusedRow("Theme")
        assertEquals(0, backCount)

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

        assertFocusedRow("Add ROM Root")

        load.trySend(Unit)
        composeRule.waitForIdle()

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

        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Scan All Consoles")

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

        assertFocusedRow("Theme")
        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Volume")

        press(GamepadAction.SELECT)
        press(GamepadAction.NAVIGATE_RIGHT)
        press(GamepadAction.NAVIGATE_RIGHT)
        press(GamepadAction.NAVIGATE_LEFT)
        assertEquals(0.6f, volume.value, 0.001f)
        assertFocusedRow("Volume")

        press(GamepadAction.BACK)
        assertEquals(0, backCount)

        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Brightness")

        press(GamepadAction.BACK)
        assertEquals(1, backCount)
    }

    @Test
    fun `first dpad press after a touch drag re-anchors to the viewport centre without moving`() {
        showScreen(onBack = {}) {
            val scrollState = rememberScrollState()
            LocalSettingsScrollStateRegistrar.current(scrollState)
            Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                repeat(30) { i -> SettingsRow(label = "Row ${i + 1}", onClick = {}) }
            }
        }

        assertFocusedRow("Row 1")

        val rowHeight = composeRule.onNode(isFocused()).fetchSemanticsNode().boundsInRoot.height

        val viewportCenter = viewportCenterY()

        composeRule.onRoot().performTouchInput {
            down(center)
            moveBy(Offset(0f, -5f))
            up()
        }
        composeRule.waitForIdle()

        val nearest: Int = (1..30).minByOrNull { i: Int ->
            val centerY = composeRule.onNodeWithText("Row $i")
                .fetchSemanticsNode().boundsInRoot.center.y
            abs(centerY - (viewportCenter + rowHeight / 2f))
        }!!

        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Row $nearest")

        press(GamepadAction.NAVIGATE_DOWN)
        assertFocusedRow("Row ${(nearest + 1).coerceAtMost(30)}")
    }

    @Test
    fun `cursor never leaves the visible viewport while walking a long list`() {
        showScreen(onBack = {}) {
            val scrollState = rememberScrollState()
            LocalSettingsScrollStateRegistrar.current(scrollState)
            Column(Modifier.fillMaxSize().verticalScroll(scrollState)) {
                repeat(40) { i -> SettingsRow(label = "Row ${i + 1}", onClick = {}) }
            }
        }
        assertFocusedRow("Row 1")

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

    private fun viewportBounds() =
        composeRule.onNodeWithTag(SettingsContentViewportTag).fetchSemanticsNode().boundsInRoot

    private fun margin(): Float = with(composeRule.density) { CONTENT_EDGE_MARGIN.toPx() }

    private fun viewportTop(): Float = viewportBounds().top + margin()

    private fun viewportBottom(): Float = viewportBounds().bottom - margin()

    private fun viewportCenterY(): Float = viewportBounds().center.y
}
