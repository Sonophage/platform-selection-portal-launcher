package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BUILT_IN_CATEGORIES
import com.psplauncher.core.domain.model.BuiltInCategory
import com.psplauncher.core.domain.model.GamepadAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPickerLogicTest {
    private fun app(pkg: String, label: String = pkg) = AppPickerEntry(packageName = pkg, label = label)

    private fun state(
        packages: List<String> = listOf("a", "b", "c", "d", "e", "f", "g", "h"),
        selected: Set<String> = emptySet(),
        initialSelected: Set<String> = emptySet(),
        focusedIndex: Int = 0,
        query: String = "",
        columns: Int = PICKER_GRID_COLUMNS,
    ) = AppPickerState(
        title = "Add Apps",
        target = AppPickerTarget.AndroidGames("android"),
        apps = packages.map { app(it) },
        selected = selected,
        initialSelected = initialSelected,
        focusedIndex = focusedIndex,
        query = query,
        columns = columns,
    )

    @Test
    fun `no query shows every app`() {
        assertEquals(8, state().visibleApps().size)
    }

    @Test
    fun `query filters by label case-insensitively`() {
        val s = state(
            packages = listOf("alpha", "Beta", "gamma"),
            query = "BET",
        )
        assertEquals(listOf("Beta"), s.visibleApps().map { it.packageName })
    }

    @Test
    fun `search never touches selected`() {
        val s = state(
            packages = listOf("a", "b"),
            selected = setOf("a", "b"),
            query = "b",
        )
        assertEquals(setOf("b"), s.visibleApps().map { it.packageName }.toSet())
        assertEquals(setOf("a", "b"), s.selected)
    }

    @Test
    fun `toggle adds then removes the same package`() {
        val afterAdd = state().toggle("a")
        assertEquals(setOf("a"), afterAdd.selected)
        val afterRemove = afterAdd.toggle("a")
        assertEquals(emptySet<String>(), afterRemove.selected)
    }

    @Test
    fun `toggle does not move focus and does not filter apps`() {
        val s = state(focusedIndex = 5).toggle("c")
        assertEquals(5, s.focusedIndex)
        assertEquals(8, s.apps.size)
    }

    @Test
    fun `toggle on an unknown package is a no-op`() {
        val s = state(selected = setOf("a"))
        val toggled = s.toggle("zzz")
        assertEquals(setOf("a"), toggled.selected)
    }

    @Test
    fun `pendingAdds is selected minus initialSelected`() {
        val s = state(selected = setOf("a", "b"), initialSelected = setOf("b", "c"))
        assertEquals(setOf("a"), s.pendingAdds())
        assertEquals(setOf("c"), s.pendingRemovals())
    }

    @Test
    fun `toggled off then back on produces empty diffs`() {
        val s = state(selected = setOf("x"), initialSelected = setOf("x"))
        val roundTrip = s.toggle("x").toggle("x")
        assertTrue(roundTrip.pendingAdds().isEmpty())
        assertTrue(roundTrip.pendingRemovals().isEmpty())
    }

    @Test
    fun `selection surviving a search round-trip diffs clean against membership`() {
        val opened = state(selected = setOf("a", "d"), initialSelected = setOf("a", "d"))
        val filtered = opened.copy(query = "d")
        assertEquals(setOf("a", "d"), filtered.selected)
        val cleared = filtered.copy(query = "")
        assertTrue(cleared.pendingAdds().isEmpty())
        assertTrue(cleared.pendingRemovals().isEmpty())
    }

    @Test
    fun `clampFocus on an empty visible list returns zero`() {
        val s = state(packages = emptyList(), focusedIndex = 3)
        assertEquals(0, s.clampFocus().focusedIndex)
    }

    @Test
    fun `clampFocus pulls a high index back when the list shrank`() {
        val shrunken = state(packages = listOf("a", "b"), focusedIndex = 7)
        assertEquals(1, shrunken.clampFocus().focusedIndex)
    }

    @Test
    fun `clampFocus after a filter removed the focused item stays in range`() {
        val filtered = state(packages = listOf("a", "b", "c", "d"), focusedIndex = 3, query = "a")
        assertEquals(0, filtered.clampFocus().focusedIndex)
    }

    @Test
    fun `clampFocus keeps a valid index unchanged`() {
        val s = state(focusedIndex = 4)
        assertEquals(4, s.clampFocus().focusedIndex)
    }

    private val sevenColumns = listOf(
        listOf("a", "b", "c", "d", "e", "f", "g"),
        listOf("h", "i", "j"),
    )

    private fun gridState(focusedIndex: Int) = state(
        packages = sevenColumns.flatten(),
        focusedIndex = focusedIndex,
    )

    @Test
    fun `move right at the last column refuses to wrap to the next row`() {
        val next = gridState(6).move(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(6, next.focusedIndex)
    }

    @Test
    fun `move left at column 0 refuses to wrap to the previous row`() {
        val next = gridState(7).move(GamepadAction.NAVIGATE_LEFT)
        assertEquals(7, next.focusedIndex)
    }

    @Test
    fun `move up past the first row is a no-op`() {
        val next = gridState(2).move(GamepadAction.NAVIGATE_UP)
        assertEquals(2, next.focusedIndex)
    }

    @Test
    fun `move down past the last row is a no-op`() {
        val next = gridState(9).move(GamepadAction.NAVIGATE_DOWN)
        assertEquals(9, next.focusedIndex)
    }

    @Test
    fun `move inside a row shifts by one`() {
        assertEquals(1, gridState(0).move(GamepadAction.NAVIGATE_RIGHT).focusedIndex)
        assertEquals(0, gridState(1).move(GamepadAction.NAVIGATE_LEFT).focusedIndex)
        assertEquals(8, gridState(1).move(GamepadAction.NAVIGATE_DOWN).focusedIndex)
        assertEquals(1, gridState(8).move(GamepadAction.NAVIGATE_UP).focusedIndex)
    }

    @Test
    fun `move down into the short last row stops at the last item`() {
        assertEquals(9, gridState(2).move(GamepadAction.NAVIGATE_DOWN).focusedIndex)
    }

    @Test
    fun `move on an empty list is a no-op`() {
        val s = state(packages = emptyList())
        assertEquals(0, s.move(GamepadAction.NAVIGATE_DOWN).focusedIndex)
    }

    @Test
    fun `confirm modal starts focused on cancel`() {
        val s = state().copy(confirmingRemovals = true)
        assertEquals(AppPickerState.CONFIRM_CANCEL, s.confirmFocusedOption)
    }

    @Test
    fun `opening the confirm modal resets option focus to cancel`() {
        val s = state().copy(confirmingRemovals = true, confirmFocusedOption = AppPickerState.CONFIRM_REMOVE)
        assertEquals(AppPickerState.CONFIRM_CANCEL, s.openConfirm().confirmFocusedOption)
    }

    @Test
    fun `confirm modal left-right moves between cancel and remove without wrap`() {
        val s = state().copy(confirmingRemovals = true)
        assertEquals(AppPickerState.CONFIRM_REMOVE, s.moveConfirm(GamepadAction.NAVIGATE_RIGHT).confirmFocusedOption)
        assertEquals(AppPickerState.CONFIRM_CANCEL, s.moveConfirm(GamepadAction.NAVIGATE_LEFT).confirmFocusedOption)
    }

    @Test
    fun `confirm modal right at remove and left at cancel are no-ops`() {
        val s = state().copy(confirmingRemovals = true, confirmFocusedOption = AppPickerState.CONFIRM_REMOVE)
        assertEquals(AppPickerState.CONFIRM_REMOVE, s.moveConfirm(GamepadAction.NAVIGATE_RIGHT).confirmFocusedOption)
        assertEquals(AppPickerState.CONFIRM_CANCEL, s.moveConfirm(GamepadAction.NAVIGATE_LEFT).confirmFocusedOption)
    }

    @Test
    fun `confirm modal ignores up-down`() {
        val s = state().copy(confirmingRemovals = true)
        assertEquals(s, s.moveConfirm(GamepadAction.NAVIGATE_UP))
        assertEquals(s, s.moveConfirm(GamepadAction.NAVIGATE_DOWN))
    }

    @Test
    fun `moveConfirm is a no-op while the modal is closed`() {
        val s = state().moveConfirm(GamepadAction.NAVIGATE_RIGHT)
        assertEquals(false, s.confirmingRemovals)
        assertEquals(AppPickerState.CONFIRM_CANCEL, s.confirmFocusedOption)
    }

    @Test
    fun `cancelConfirm resets option focus to cancel`() {
        val s = state().copy(confirmingRemovals = true, confirmFocusedOption = AppPickerState.CONFIRM_REMOVE)
        val cancelled = s.cancelConfirm()
        assertEquals(false, cancelled.confirmingRemovals)
        assertEquals(AppPickerState.CONFIRM_CANCEL, cancelled.confirmFocusedOption)
    }

    private fun builtIn(id: String) = BUILT_IN_CATEGORIES.first { it.id == id }

    @Test
    fun `an app can be added to the columns that list apps`() {
        listOf("music", "videos", "photos", "network", BuiltInCategory.LIBRARY).forEach { id ->
            assertTrue("$id builds its column from assigned apps", categoryShowsApps(builtIn(id)))
        }
    }

    @Test
    fun `Last Played refuses apps although it is not a gaming category`() {
        val recents = builtIn(BuiltInCategory.RECENTLY_PLAYED)

        assertEquals(false, recents.isGamingCategory)
        assertEquals(false, categoryShowsApps(recents))
    }

    @Test
    fun `Settings and gaming columns refuse apps`() {
        assertEquals(false, categoryShowsApps(builtIn(BuiltInCategory.SETTINGS)))
        assertEquals(false, categoryShowsApps(builtIn(BuiltInCategory.GAMES)))
    }

    @Test
    fun `down steps one drawn row, whatever the panel measured`() {
        val packages = (1..40).map { "p$it" }
        for (cols in 3..12) {
            val moved = state(packages = packages, focusedIndex = 0, columns = cols)
                .move(GamepadAction.NAVIGATE_DOWN)
            assertEquals(
                "with a $cols-wide grid, down from the first tile must land one row below",
                cols,
                moved.focusedIndex,
            )
        }
    }

    @Test
    fun `right stops at the end of the drawn row`() {
        val packages = (1..40).map { "p$it" }
        for (cols in 3..12) {
            val atRowEnd = state(packages = packages, focusedIndex = cols - 1, columns = cols)
            val moved = atRowEnd.move(GamepadAction.NAVIGATE_RIGHT)
            assertEquals(
                "with a $cols-wide grid, right at the row's last tile must not wrap to the next row",
                cols - 1,
                moved.focusedIndex,
            )
        }
    }

    @Test
    fun `a zero or negative column count is survived`() {
        for (cols in -3..0) {
            val moved = state(focusedIndex = 2, columns = cols).move(GamepadAction.NAVIGATE_DOWN)
            assertTrue("focus stays in range at columns=$cols", moved.focusedIndex in 0..7)
        }
    }
}
