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

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w480dp-h640dp")
class AppPickerThreeRowsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `three full tile rows fit above the footer on a small screen`() {
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

        val gridNode: SemanticsNodeInteraction = composeRule.onNode(hasVerticalScrollAction())
        val gridBottom = gridNode.fetchSemanticsNode().boundsInRoot.bottom

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

        val rootBottom = composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom

        val minFooterSlot = with(composeRule.density) { HintBarHeight.toPx() }
        assert(gridBottom <= rootBottom - minFooterSlot) {
            "grid viewport bottom $gridBottom runs into the footer slot (root bottom $rootBottom)"
        }
    }

    private fun hasVerticalScrollAction() = SemanticsMatcher("has scroll-to-index action") { node ->
        node.config.contains(SemanticsActions.ScrollToIndex)
    }
}
