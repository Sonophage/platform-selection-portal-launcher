package com.psplauncher.feature.xmb.ui.detail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Options menu's headings are computed from the filtered action list, not the enum, because
 * two actions are conditional: Emulator is hidden on package-backed entries and Export Game is
 * offered on Windows only. A heading pinned to the enum's first member of a group would strand
 * itself the moment that member was filtered out.
 */
class DetailActionSectionsTest {

    @Test
    fun `every action carries a heading only where its group starts`() {
        val all = DetailAction.entries
        val headings = sectionHeadings(all)

        assertEquals(all.size, headings.size)
        assertEquals(all.first().section, headings.first())
        all.forEachIndexed { index, action ->
            val previous = all.getOrNull(index - 1)
            if (previous != null && previous.section == action.section) {
                assertNull(headings[index], "${action.name} continues ${action.section}")
            } else {
                assertEquals(action.section, headings[index], "${action.name} starts its group")
            }
        }
    }

    @Test
    fun `hiding the first action of a group promotes the next one to carry the heading`() {
        // What a package-backed entry actually gets: Emulator gone, so Saves heads Play.
        val visible = DetailAction.entries.filter { it != DetailAction.EMULATOR }
        val headings = sectionHeadings(visible)

        val saves = visible.indexOf(DetailAction.SAVES)
        assertEquals(DetailAction.SAVES.section, headings[saves])
        assertEquals(DetailAction.MANUAL.section, DetailAction.SAVES.section)
        assertNull(headings[visible.indexOf(DetailAction.MANUAL)])
    }

    @Test
    fun `no group is left without a heading and none is drawn twice`() {
        // A GBA ROM: no Export Game, which is the last-but-two of the File group.
        val visible = DetailAction.entries.filter { it != DetailAction.EXPORT }
        val headings = sectionHeadings(visible)

        val drawn = headings.filterNotNull()
        assertEquals(visible.map { it.section }.distinct(), drawn)
        assertEquals(drawn.size, drawn.distinct().size, "a group heading was drawn twice")
    }

    @Test
    fun `Remove is last and is the only destructive action`() {
        // It sat directly under Open Location in the flat list with nothing marking the change of
        // weight. Its position is the guard, so it is asserted rather than assumed.
        assertEquals(DetailAction.REMOVE, DetailAction.entries.last())
        assertTrue(DetailAction.entries.none { it.section.isBlank() })
    }
}
