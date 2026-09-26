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

        val rootBottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val footerSlot = with(composeRule.density) { 24.dp.toPx() }
        val floor = rootBottom - footerSlot

        val first = composeRule.onAllNodesWithText("Other 1").fetchSemanticsNodes()
        val last = composeRule.onAllNodesWithText("Other $SECTION_LIST_ROWS").fetchSemanticsNodes()
        assert(first.isNotEmpty() && last.isNotEmpty()) { "the list did not compose its first column" }

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

    private val MinReadableRow = 18.dp
}
