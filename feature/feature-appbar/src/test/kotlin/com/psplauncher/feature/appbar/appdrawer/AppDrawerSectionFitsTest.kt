package com.psplauncher.feature.appbar.appdrawer

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.psplauncher.core.ui.preview.PfpScreenPreview
import com.psplauncher.feature.appbar.AppDrawerContent
import com.psplauncher.feature.appbar.AppDrawerUiState
import com.psplauncher.feature.appbar.AppFilter
import com.psplauncher.feature.appbar.InstalledApp
import com.psplauncher.feature.appbar.SECTION_LIST_ROWS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The 8q body stays usable on a small screen (Robolectric JVM Compose test).
 *
 * It replaces a three-rows-of-tiles test that guarded the All Apps grid, which no longer exists.
 *
 * It does NOT assert that six rows fit, which is what was written here first and was worthless:
 * `GridCells.Fixed(SECTION_LIST_ROWS)` divides whatever height it is given into six, so six rows
 * always "fit". With the row height forced to 200dp on a 640dp panel that assertion still passed.
 *
 * What is actually at risk is the opposite. The tile row and the two headings are measured first
 * and the list takes what is left, so on a short panel the list does not lose rows — it gets
 * crushed into unreadable ones. The guard is therefore a minimum row height; the list being
 * present at all is not evidence of anything.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h640dp")
class AppDrawerSectionFitsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun app(label: String, emulator: Boolean) = InstalledApp(
        packageName = "com.test." + label.replace(" ", "").lowercase(),
        label = label,
        icon = android.graphics.drawable.ColorDrawable(android.graphics.Color.GRAY),
        isGame = false,
        isEmulator = emulator,
    )

    @Test
    fun `the compact list keeps readable rows above the footer on a small screen`() {
        val matched = (1..6).map { app("Emu $it", emulator = true) }
        // 24 in the list: four full columns, so the first column is full and the list has
        // something to scroll sideways.
        val rest = (1..24).map { app("Other $it", emulator = false) }
        val state = AppDrawerUiState(
            allApps = matched + rest,
            sectionApps = matched,
            otherApps = rest,
            activeFilter = AppFilter.EMULATORS,
            isLoading = false,
            selectedIndex = 0,
        )

        composeRule.setContent {
            PfpScreenPreview {
                AppDrawerContent(
                    state = state,
                    searchActive = false,
                    showControllerHint = false,
                    onBack = {},
                    onSearchQueryChange = {},
                    onSearchToggle = {},
                    onSearchDone = {},
                    onFilterSelected = {},
                    onAppTapped = {},
                    onAppLaunched = {},
                    onAppMenu = {},
                    onTouchBrowse = {},
                    onMenuAction = {},
                    onCloseMenu = {},
                    onConfirmUninstall = {},
                    onCancelUninstall = {},
                    onGrantUsageAccess = {},
                )
            }
        }
        composeRule.waitForIdle()

        // The footer slot is alpha-reserved, so it holds its height whether or not the hint pill
        // is visible. Anything drawn past this line is under it.
        val rootBottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val footerSlot = with(composeRule.density) { 24.dp.toPx() } // pill padding (12dp x 2)
        val floor = rootBottom - footerSlot

        // The list fills column-first, so the first column's entries ARE rows 1..6.
        val first = composeRule.onAllNodesWithText("Other 1").fetchSemanticsNodes()
        val last = composeRule.onAllNodesWithText("Other $SECTION_LIST_ROWS").fetchSemanticsNodes()
        assert(first.isNotEmpty() && last.isNotEmpty()) { "the list did not compose its first column" }

        // positionInRoot, NOT boundsInRoot: boundsInRoot is clipped to its ancestors, so anything
        // pushed under the footer reports an edge sitting exactly ON the viewport line and every
        // bounds comparison passes. That is the other way the first version of this test managed
        // to be green while the layout was wrong.
        val firstTop = first[0].positionInRoot.y
        val lastTop = last[0].positionInRoot.y
        val rowHeight = (lastTop - firstTop) / (SECTION_LIST_ROWS - 1)
        val minRow = with(composeRule.density) { MinReadableRow.toPx() }
        assert(rowHeight >= minRow) {
            "list rows crushed to $rowHeight px, below the $MinReadableRow a 13sp label needs"
        }

        val listBottom = lastTop + last[0].size.height
        assert(listBottom <= floor) {
            "the list runs into the footer slot (bottom $listBottom, floor $floor)"
        }
    }

    /** Below this a 13sp label has nowhere to sit. */
    private val MinReadableRow = 18.dp
}
