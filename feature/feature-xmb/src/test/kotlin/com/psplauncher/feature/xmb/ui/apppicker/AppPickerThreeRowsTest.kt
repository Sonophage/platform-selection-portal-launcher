package com.psplauncher.feature.xmb.ui.apppicker

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import com.psplauncher.core.ui.components.HintBarHeight
import com.psplauncher.core.ui.preview.PfpScreenPreview
import com.psplauncher.feature.xmb.viewmodel.AppPickerEntry
import com.psplauncher.feature.xmb.viewmodel.AppPickerState
import com.psplauncher.feature.xmb.viewmodel.AppPickerTarget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real [AppPickerScreen] with 28 apps on a w480dp-h640dp screen.
 *
 * **It cannot check that the third row FITS, and that is measured, not suspected.** Forcing a real
 * 122dp overflow (artwork floored at 120dp against a 450dp viewport) leaves this test green:
 * `boundsInRoot` is clipped to the viewport, so a tile hanging below the grid reports a bottom
 * equal to the grid's and "nothing extends past the bottom" stays true. The sum is checked in
 * `AppPickerRowFitTest` instead, which is falsifiable — lowering the artwork floor turns it red.
 *
 * What this test does still earn: the grid composes at all, tiles are composed with their labels,
 * at least three rows are laid out, and the grid stops short of the root by the footer's height —
 * a positional fact that clipping does not hide.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h640dp")
class AppPickerThreeRowsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `three full tile rows fit above the footer on a small screen`() {
        // Comfortably more tiles than three rows can hold at any column count this screen
        // measures, so the grid always has something to scroll and the assertions bite.
        val apps = (1..28).map { i ->
            AppPickerEntry(
                packageName = "com.test.app$i",
                label = "App $i",
                icon = android.graphics.drawable.ColorDrawable(
                    if (i % 2 == 0) android.graphics.Color.GRAY else android.graphics.Color.DKGRAY,
                ),
            )
        }
        val state = AppPickerState(
            title = "Add Apps",
            target = AppPickerTarget.AndroidGames(platformId = "android"),
            apps = apps,
        )

        composeRule.setContent {
            PfpScreenPreview {
                AppPickerScreen(
                    state = state,
                    onTileTapped = {},
                    onTouchBrowse = {},
                    onHeaderBack = {},
                    onSearchToggle = {},
                    onSearchChange = {},
                    onSearchDone = {},
                    onApply = {},
                    onConfirmRemoval = {},
                    onCancelRemoval = {},
                )
            }
        }
        composeRule.waitForIdle()

        // The picker's LazyVerticalGrid is the only vertically scrollable node in the content.
        val gridNode: SemanticsNodeInteraction = composeRule.onNode(hasVerticalScrollAction())
        val gridBottom = gridNode.fetchSemanticsNode().boundsInRoot.bottom

        // Every composed tile must sit fully inside the grid viewport: a bottom edge past the
        // grid's bottom means a row is clipped under the footer slot. Tile icons carry the app
        // label as contentDescription, so "App <n>" matches each tile.
        val visibleTiles = composeRule
            .onAllNodesWithContentDescription("App ", substring = true)
            .fetchSemanticsNodes()
        assert(visibleTiles.isNotEmpty()) { "expected the grid to have visible tiles" }
        visibleTiles.forEach { node ->
            val bottom = node.boundsInRoot.bottom
            assert(bottom <= gridBottom + 0.5f) {
                "tile bottom $bottom extends past grid viewport bottom $gridBottom — clipped under the footer"
            }
        }

        // THREE ROWS, counted as rows.
        //
        // This asserted that "App 21" was on screen, which is row three only while the grid is
        // seven columns wide. The column count is measured from the panel now — this test's own
        // w480dp qualifier gives three — so tile 21 moved to row seven and the assertion failed
        // for a layout that is correct. The number 21 was a second copy of the column count,
        // exactly the kind this file's footer assertion already warns about.
        //
        // Rows are what the contract is about, so rows are what is counted: tiles that share a
        // top edge are a row.
        val rowTops = visibleTiles.map { kotlin.math.round(it.boundsInRoot.top) }.distinct().sorted()
        assert(rowTops.size >= 3) {
            "only ${rowTops.size} full tile row(s) visible in the viewport — the sizing guarantees three"
        }
        val thirdRowBottom = visibleTiles
            .filter { kotlin.math.round(it.boundsInRoot.top) == rowTops[2] }
            .maxOf { it.boundsInRoot.bottom }
        assert(thirdRowBottom <= gridBottom) {
            "third row's bottom $thirdRowBottom extends past grid viewport bottom $gridBottom"
        }

        // Footer-slot invariant: the permanent footer lives below the grid, so the grid may not
        // run to the root bottom edge — there must be real clearance of at least the bar's height.
        val rootBottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        // The footer is core-ui's shared PfpHintBar now, so the clearance this asserts is read
        // from HintBarHeight rather than copied here as a number. The old footer was an INLINE
        // prompt row with 12dp of padding either side, and "24" written in this test was the
        // second copy of that — the copy that would have kept passing after the bar changed.
        val minFooterSlot = with(composeRule.density) { HintBarHeight.toPx() }
        assert(gridBottom <= rootBottom - minFooterSlot) {
            "grid viewport bottom $gridBottom runs into the footer slot (root bottom $rootBottom)"
        }
    }

    /** The picker's LazyVerticalGrid: the only node carrying the lazy-grid scroll-to-index action. */
    private fun hasVerticalScrollAction() = SemanticsMatcher("has scroll-to-index action") { node ->
        node.config.contains(SemanticsActions.ScrollToIndex)
    }
}
