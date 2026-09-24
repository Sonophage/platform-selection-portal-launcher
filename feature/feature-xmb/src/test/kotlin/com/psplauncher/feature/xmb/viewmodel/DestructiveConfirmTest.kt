package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the cursor opens on a prompt that can delete something.
 *
 * Both confirms existed and both opened on their destructive answer. Remove From Library is
 * reached by holding DOWN to the bottom of the rail and pressing A, and the prompt that came up
 * answered a second A with yes — the press that gets you there is the press most likely to arrive
 * again. The App Drawer's uninstall prompt was fixed for this and nothing was checking these.
 *
 * The builders are the REAL ones the ViewModel calls. An earlier version of this file built its
 * own rows and asserted on them, which could not fail however the menus changed.
 */
class DestructiveConfirmTest {

    private val confirms = listOf(
        "remove from library" to removeGameConfirmItems(),
        "remove permanently" to removeMissingConfirmItems(),
    )

    @Test
    fun `every destructive confirm opens with the cursor on the harmless answer`() {
        // The cursor opens at index 0 on every menu in the app, so "which row is first" IS where
        // the cursor is. Asserted on the row rather than on a selectedIndex, because a menu that
        // opened at index 1 would pass an index check and break the day someone reordered it.
        confirms.forEach { (name, rows) ->
            assertFalse("$name: the first row is the destructive one", rows.first().isDestructive)
            assertEquals("$name: the first row is not Cancel", "Cancel", rows.first().label)
        }
    }

    @Test
    fun `each still offers the destructive answer, and marks it`() {
        // The tint and the rail's never-cut rule both key off isDestructive. Moving the row to
        // second must not have dropped the flag that makes it red and makes it survive the cap.
        confirms.forEach { (name, rows) ->
            assertEquals("$name: a confirm is two rows", 2, rows.size)
            assertTrue("$name: nothing here is marked destructive", rows.any { it.isDestructive })
        }
    }

    @Test
    fun `the ids the handlers match on are unchanged`() {
        // Reordering rows is safe; renaming them is not, and it fails silently — the press just
        // stops doing anything, which is the same trap PillActionsTest guards.
        assertEquals(
            listOf("cancel_remove_game", "confirm_remove_game"),
            removeGameConfirmItems().map { it.id },
        )
        assertEquals(
            listOf("cancel_remove_missing", "confirm_remove_missing"),
            removeMissingConfirmItems().map { it.id },
        )
    }
}
