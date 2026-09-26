package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.feature.xmb.viewmodel.MenuGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DetailActionSectionsTest {
    @Test
    fun `every action carries a heading only where its group starts`() {
        val all = DetailAction.entries
        val headings = sectionHeadings(all)

        assertEquals(all.size, headings.size)
        assertEquals(all.first().group.heading, headings.first())
        all.forEachIndexed { index, action ->
            val previous = all.getOrNull(index - 1)
            if (previous != null && previous.group == action.group) {
                assertNull(headings[index], "${action.name} continues ${action.group}")
            } else {
                assertEquals(action.group.heading, headings[index], "${action.name} starts its group")
            }
        }
    }

    @Test
    fun `hiding the first action of a group promotes the next one to carry the heading`() {
        val visible = DetailAction.entries.filter { it != DetailAction.FAVORITE }
        val headings = sectionHeadings(visible)

        assertEquals(DetailAction.COLLECTIONS.group, DetailAction.FAVORITE.group)
        assertEquals("Library", headings[visible.indexOf(DetailAction.COLLECTIONS)])
    }

    @Test
    fun `no group is left without a heading and none is drawn twice`() {
        val visible = DetailAction.entries.filter { it != DetailAction.EMULATOR }
        val drawn = sectionHeadings(visible).filterNotNull()

        assertEquals(visible.mapNotNull { it.group.heading }.distinct(), drawn)
        assertEquals(drawn.size, drawn.distinct().size, "a group heading was drawn twice")
    }

    @Test
    fun `Remove is last and is the only action in its group`() {
        assertEquals(DetailAction.REMOVE, DetailAction.entries.last())
        assertTrue(DetailAction.entries.none { it != DetailAction.REMOVE && it.group == MenuGroup.REMOVE })
    }

    @Test
    fun `the detail page ranks its groups the way every other menu does`() {
        val ranks = DetailAction.entries.map { it.group.ordinal }
        assertEquals(ranks.sorted(), ranks, "a detail action sits outside its group's rank")
    }
}
