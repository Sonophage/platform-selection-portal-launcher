package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import com.psplauncher.core.domain.model.TouchNavButtonMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.psplauncher.core.ui.components.MenuGroup
import com.psplauncher.core.ui.components.MenuState
import com.psplauncher.core.ui.components.rowsShown
import com.psplauncher.core.ui.components.foldedIntoGroups

class ContextMenuHintStateTest {
    private val gameItem = XMBItem(id = "g1", title = "Game", gameId = 1L)

    private val IDLE_MS = 0L

    private fun eligibleState(idleMs: Long = IDLE_MS) = XMBUiState(
        categories = listOf(Category(BuiltInCategory.GAMES, "Games", "games", type = CategoryType.BUILT_IN, position = 0)),
        selectedCategoryIndex = 0,
        currentItems = listOf(gameItem),
        selectedItemIndex = 0,
        lastInputWasTouch = false,
        touchNavButtonMode = TouchNavButtonMode.AUTO,
        showBootSequence = false,
    ).let { it.copy(showContextMenuHint = false) }

    @Test
    fun `shows hint when idle long enough over a context-menu item after controller input`() {
        assertTrue(shouldShowContextMenuHint(eligibleState(), IDLE_MS))
    }

    @Test
    fun `an empty home shelf still shows the pill, because X is how you leave it`() {
        val emptyHome = XMBUiState(
            categories = listOf(
                Category(
                    BuiltInCategory.RECENTLY_PLAYED, "Last Played", "recent",
                    type = CategoryType.BUILT_IN, position = 0,
                ),
            ),
            selectedCategoryIndex = 0,
            currentItems = emptyList(),
            lastInputWasTouch = false,
            showBootSequence = false,
            recentFilter = RecentFilter.MUSIC,
        )

        assertFalse("nothing is focused, so there is no context menu", emptyHome.focusedItemHasContextMenu)
        assertFalse("Last Played does not sort", emptyHome.canSortCurrentList)
        assertTrue("but X still filters, so the pill has something true to say", emptyHome.canFilterRecents)
        assertTrue(shouldShowContextMenuHint(emptyHome, IDLE_MS))
    }

    @Test
    fun `the default delay of zero means the hint is up immediately`() {
        assertTrue(shouldShowContextMenuHint(eligibleState(), 0L))
    }

    @Test
    fun `still shows after touch input, because the prompts can be tapped`() {
        val s = eligibleState().copy(lastInputWasTouch = true)
        assertTrue(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `shows regardless of touch-button mode`() {
        val s = eligibleState().copy(
            lastInputWasTouch = false,
            touchNavButtonMode = TouchNavButtonMode.ALWAYS_HIDE,
        )
        assertTrue(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `hints do not auto-hide at the default delay of zero`() {
        assertFalse(eligibleState().hintsAutoHide)
    }

    @Test
    fun `hints auto-hide as soon as any delay is configured`() {
        assertTrue(eligibleState().copy(contextMenuHintDelaySeconds = 0.5f).hintsAutoHide)
        assertTrue(eligibleState().copy(contextMenuHintDelaySeconds = 5f).hintsAutoHide)
    }

    @Test
    fun `recomputing raises the flag on the spot rather than waiting for the poller`() {
        val s = eligibleState().copy(showContextMenuHint = false)
        assertTrue(s.withHintsShownNow().showContextMenuHint)
    }

    @Test
    fun `recomputing is idempotent, because no gate reads a hint flag`() {
        val once = eligibleState().withHintsShownNow()
        assertEquals(once, once.withHintsShownNow())
    }

    @Test
    fun `recomputing lowers a flag whose gate no longer holds`() {
        val stale = eligibleState().copy(
            showContextMenuHint = true,
            activeGameId = 1L,
        )
        assertFalse(stale.withHintsShownNow().showContextMenuHint)
    }

    @Test
    fun `the setting still switches the hints off entirely`() {
        val s = eligibleState().copy(contextMenuHintEnabled = false)
        assertFalse(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `a configured delay still holds the hint back until the pause has elapsed`() {
        val s = eligibleState().copy(contextMenuHintDelaySeconds = 2.5f)
        assertFalse(shouldShowContextMenuHint(s, 2_499L))
        assertTrue(shouldShowContextMenuHint(s, 2_500L))
    }

    @Test
    fun `does not show when a blocking overlay is up`() {
        val s = eligibleState().copy(activeGameId = 1L)
        assertFalse(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `shows over the context rail, which is the one overlay it survives`() {
        val s = eligibleState().copy(activeContextMenu = XMBContextMenu(state = MenuState(title = "X", rows = emptyList())))
        assertTrue(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `a context menu on top of a REAL blocking overlay still hides it`() {
        val s = eligibleState().copy(
            activeContextMenu = XMBContextMenu(state = MenuState(title = "X", rows = emptyList())),
            activeGameId = 1L,
        )
        assertFalse(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `does not show when the focused item has no context menu and the list cannot sort`() {
        val plain = XMBItem(id = "x", title = "Plain", type = XMBItemType.STANDARD)
        val s = eligibleState().copy(currentItems = listOf(plain))
        assertFalse(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `shows while drilled into a sub-item`() {
        val s = eligibleState().copy(selectedPlatformId = "psp")
        assertTrue(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `an unsortable root list offers no sort prompt`() {
        assertFalse(eligibleState().canSortCurrentList)
    }

    @Test
    fun `drilling into a platform makes the list sortable`() {
        assertTrue(eligibleState().copy(selectedPlatformId = "psp").canSortCurrentList)
        assertTrue(eligibleState().copy(selectedCollectionId = 7L).canSortCurrentList)
    }

    @Test
    fun `a sortable list shows the pill even when the focused item has no context menu`() {
        val plain = XMBItem(id = "x", title = "Plain", type = XMBItemType.STANDARD)
        val s = eligibleState().copy(currentItems = listOf(plain), selectedPlatformId = "psp")
        assertFalse(s.focusedItemHasContextMenu)
        assertTrue(s.canSortCurrentList)
        assertTrue(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `neither half applicable means no pill`() {
        val plain = XMBItem(id = "x", title = "Plain", type = XMBItemType.STANDARD)
        val s = eligibleState().copy(currentItems = listOf(plain))
        assertFalse(s.focusedItemHasContextMenu)
        assertFalse(s.canSortCurrentList)
        assertFalse(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `does not show when boot sequence is still playing`() {
        val s = eligibleState().copy(showBootSequence = true)
        assertFalse(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `does not show when the context-menu hint setting is disabled`() {
        val s = eligibleState().copy(contextMenuHintEnabled = false)
        assertFalse(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `uses the configured delay instead of the default delay`() {
        val s = eligibleState().copy(contextMenuHintDelaySeconds = 4.5f)
        assertFalse(shouldShowContextMenuHint(s, 4_499))
        assertTrue(shouldShowContextMenuHint(s, 4_500))
    }

    @Test
    fun `delay values below one second and above five seconds are not treated specially by the pure gate`() {
        val oneSecond = eligibleState().copy(contextMenuHintDelaySeconds = 1f)
        val fiveSeconds = eligibleState().copy(contextMenuHintDelaySeconds = 5f)
        assertTrue(shouldShowContextMenuHint(oneSecond, 1_000))
        assertFalse(shouldShowContextMenuHint(fiveSeconds, 4_999))
        assertTrue(shouldShowContextMenuHint(fiveSeconds, 5_000))
    }

    private fun drawerEligibleState() = XMBUiState(
        activeAppDrawerFilter = "ALL",
        lastInputWasTouch = false,
        showBootSequence = false,
    ).let { it.copy(showAppDrawerHint = false) }

    @Test
    fun `drawer hint shows when idle long enough with a controller while the drawer is open`() {
        assertTrue(shouldShowAppDrawerHint(drawerEligibleState(), IDLE_MS))
    }

    @Test
    fun `a configured delay still holds the drawer hint back`() {
        val s = drawerEligibleState().copy(contextMenuHintDelaySeconds = 2.5f)
        assertFalse(shouldShowAppDrawerHint(s, 2_499L))
        assertTrue(shouldShowAppDrawerHint(s, 2_500L))
    }

    @Test
    fun `drawer hint never shows while the drawer is closed`() {
        val s = eligibleState()
        assertFalse(shouldShowAppDrawerHint(s, IDLE_MS))
        assertTrue(shouldShowContextMenuHint(s, IDLE_MS))
    }

    @Test
    fun `drawer hint still shows after touch input`() {
        val s = drawerEligibleState().copy(lastInputWasTouch = true)
        assertTrue(shouldShowAppDrawerHint(s, IDLE_MS))
    }

    @Test
    fun `drawer hint does not show when a context menu is open`() {
        val s = drawerEligibleState().copy(activeContextMenu = XMBContextMenu(state = MenuState(title = "X", rows = emptyList())))
        assertFalse(shouldShowAppDrawerHint(s, IDLE_MS))
    }

    @Test
    fun `drawer hint stops when the context-menu hint setting is disabled`() {
        val s = drawerEligibleState().copy(contextMenuHintEnabled = false)
        assertFalse(shouldShowAppDrawerHint(s, IDLE_MS))
    }

    @Test
    fun `drawer hint uses the configured delay instead of the default delay`() {
        val s = drawerEligibleState().copy(contextMenuHintDelaySeconds = 4.5f)
        assertFalse(shouldShowAppDrawerHint(s, 4_499))
        assertTrue(shouldShowAppDrawerHint(s, 4_500))
    }

    @Test
    fun `drawer open is a blocking overlay so the drawer hint and XMB pill are mutually exclusive`() {
        val open = drawerEligibleState()
        assertTrue(shouldShowAppDrawerHint(open, IDLE_MS))
        assertFalse(shouldShowContextMenuHint(open, IDLE_MS))
    }

    private fun settingsEligibleState(screenId: String = "settings_audio") = XMBUiState(
        activeSettingsScreen = screenId,
        lastInputWasTouch = false,
        showBootSequence = false,
    ).let { it.copy(showSettingsHint = false) }

    @Test
    fun `settings hint shows after the shared idle delay`() {
        assertTrue(shouldShowSettingsHint(settingsEligibleState(), IDLE_MS))
    }

    @Test
    fun `a configured delay still holds the settings hint back`() {
        val s = settingsEligibleState().copy(contextMenuHintDelaySeconds = 2.5f)
        assertFalse(shouldShowSettingsHint(s, 2_499L))
        assertTrue(shouldShowSettingsHint(s, 2_500L))
    }

    @Test
    fun `settings hint still shows after touch input`() {
        assertTrue(
            shouldShowSettingsHint(
                settingsEligibleState().copy(lastInputWasTouch = true),
                IDLE_MS,
            )
        )
    }

    @Test
    fun `settings hint shows on the Display screen too`() {
        assertTrue(
            shouldShowSettingsHint(
                settingsEligibleState("settings_display"),
                IDLE_MS,
            )
        )
    }

    @Test
    fun `settings hint shows on a screen with no prompts of its own`() {
        assertTrue(
            shouldShowSettingsHint(
                settingsEligibleState("settings_about"),
                IDLE_MS,
            )
        )
    }

    @Test
    fun `settings hint does not show when no settings screen is open`() {
        assertFalse(
            shouldShowSettingsHint(
                settingsEligibleState().copy(activeSettingsScreen = null),
                IDLE_MS,
            )
        )
    }

    @Test
    fun `settings hint respects the shared setting and configured delay`() {
        val disabled = settingsEligibleState().copy(contextMenuHintEnabled = false)
        assertFalse(shouldShowSettingsHint(disabled, IDLE_MS))

        val delayed = settingsEligibleState().copy(contextMenuHintDelaySeconds = 4.5f)
        assertFalse(shouldShowSettingsHint(delayed, 4_499))
        assertTrue(shouldShowSettingsHint(delayed, 4_500))
    }
}
