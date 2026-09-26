package com.psplauncher.feature.xmb.ui

import com.psplauncher.core.domain.model.Category
import com.psplauncher.core.domain.model.CategoryType
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class XmbCategoryBarVisibilityTest {
    private fun category(id: String, position: Int) = Category(
        id = id,
        name = id,
        iconKey = id,
        type = CategoryType.BUILT_IN,
        position = position,
    )

    private val categories = listOf("settings", "photo", "music", "video", "game", "network")
        .mapIndexed { i, id -> category(id, i) }

    @Test
    fun `undrilled, every category is laid out`() {
        assertSame(categories, visibleCategories(categories, selectedIndex = 4, drilledIn = false))
    }

    @Test
    fun `drilled in, the categories right of the active one are dropped`() {
        val visible = visibleCategories(categories, selectedIndex = 4, drilledIn = true)

        assertEquals(listOf("settings", "photo", "music", "video", "game"), visible.map { it.id })
    }

    @Test
    fun `the active category is the last laid-out slot, so nothing trails it`() {
        val visible = visibleCategories(categories, selectedIndex = 4, drilledIn = true)

        assertEquals(visible.lastIndex, 4)
    }

    @Test
    fun `surviving indices still line up with the source list`() {
        val visible = visibleCategories(categories, selectedIndex = 3, drilledIn = true)

        visible.forEachIndexed { i, c -> assertEquals(categories[i].id, c.id) }
        assertEquals("video", visible[3].id)
    }

    @Test
    fun `drilling into the first category leaves exactly one slot`() {
        val visible = visibleCategories(categories, selectedIndex = 0, drilledIn = true)

        assertEquals(listOf("settings"), visible.map { it.id })
    }

    @Test
    fun `drilling into the last category drops nothing`() {
        val visible = visibleCategories(categories, selectedIndex = categories.lastIndex, drilledIn = true)

        assertEquals(categories.map { it.id }, visible.map { it.id })
    }

    @Test
    fun `a selection that is not a real index hides nothing`() {
        assertSame(categories, visibleCategories(categories, selectedIndex = -1, drilledIn = true))
        assertSame(categories, visibleCategories(categories, selectedIndex = 99, drilledIn = true))
    }

    @Test
    fun `an empty category list survives every combination`() {
        assertEquals(emptyList(), visibleCategories(emptyList(), selectedIndex = 0, drilledIn = true))
        assertEquals(emptyList(), visibleCategories(emptyList(), selectedIndex = -1, drilledIn = false))
    }
}
