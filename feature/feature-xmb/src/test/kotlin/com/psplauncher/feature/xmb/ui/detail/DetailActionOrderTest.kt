package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.feature.xmb.viewmodel.MenuGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DetailActionOrderTest {
    @Test
    fun `the detail page ranks its groups the way every other menu does`() {
        val ranks = DetailAction.entries.map { it.group.ordinal }
        assertEquals(ranks.sorted(), ranks, "a detail action sits outside its group's rank")
    }

    @Test
    fun `Remove is last and is the only action in its group`() {
        assertEquals(DetailAction.REMOVE, DetailAction.entries.last())
        assertTrue(DetailAction.entries.none { it != DetailAction.REMOVE && it.group == MenuGroup.REMOVE })
    }

    @Test
    fun `the menu opens on an action that does something`() {
        assertEquals(
            DetailAction.FAVORITE,
            DetailAction.entries.first(),
            "the Options menu opens with the cursor on a stub",
        )
    }
}
