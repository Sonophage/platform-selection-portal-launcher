package com.psplauncher.feature.xmb.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DestructiveConfirmTest {
    private val confirms = listOf(
        "remove from library" to removeGameConfirmItems(),
        "remove permanently" to removeMissingConfirmItems(),
    )

    @Test
    fun `every destructive confirm opens with the cursor on the harmless answer`() {
        confirms.forEach { (name, rows) ->
            assertFalse("$name: the first row is the destructive one", rows.first().isDestructive)
            assertEquals("$name: the first row is not Cancel", "Cancel", rows.first().label)
        }
    }

    @Test
    fun `each still offers the destructive answer, and marks it`() {
        confirms.forEach { (name, rows) ->
            assertEquals("$name: a confirm is two rows", 2, rows.size)
            assertTrue("$name: nothing here is marked destructive", rows.any { it.isDestructive })
        }
    }

    @Test
    fun `the ids the handlers match on are unchanged`() {
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
