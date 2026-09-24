package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.BUILT_IN_CATEGORIES
import com.psplauncher.core.domain.model.BuiltInCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The built-in categories were defined twice and had already drifted.
 *
 * `CategoryRepositoryImpl.BUILT_IN_CATEGORIES` called itself the single source of truth and gained
 * a Library row. `XMBViewModel.FALLBACK_CATEGORIES` was a byte-for-byte copy of the other seven and
 * never did. That was not only a cold-start difference:
 *
 *  * `canonicalXmbCategories` derives its set of built-in ids from the crossbar's copy, so Library
 *    missed the built-in branch, fell through to the custom-category path, and lost the canonical
 *    icon every other built-in is guaranteed.
 *  * `observeCategoryBar` does `categories.ifEmpty { FALLBACK_CATEGORIES }`, so on an empty read
 *    the Library section disappeared from the bar entirely.
 *
 * The sting: `canonicalXmbCategories` exists BECAUSE the bar and the repository drifted apart once
 * before. The merge rule was guarded; the two lists it merged were not.
 *
 * The fix was not to test that two copies agree. It was to have one list, in core-domain, which
 * both sides now read. These tests hold that line.
 */
class CategoryBarFallbackTest {

    @Test
    fun `the crossbar's fallback IS the canonical list, not a copy of it`() {
        // Identity, not equality. A future edit that reintroduces a hand-written duplicate here
        // would still pass an equality check on the day it was written, and drift afterwards.
        assertSame(BUILT_IN_CATEGORIES, XMBViewModel.FALLBACK_CATEGORIES)
    }

    @Test
    fun `Library is in it, which is the drift this was written for`() {
        assertTrue(BUILT_IN_CATEGORIES.any { it.id == BuiltInCategory.LIBRARY })
    }

    @Test
    fun `a stored built-in keeps its canonical icon instead of the database's`() {
        // THE consequence of the drift, stated as the property it broke.
        //
        // canonicalXmbCategories reads its builtInIds from this list. A category present in the
        // database but missing from the list is not recognised as built-in, so it falls through to
        // customCategories and is passed through VERBATIM -- keeping whatever icon key the row
        // happens to carry instead of the canonical one every built-in is guaranteed. Library was
        // in exactly that position.
        val storedWithJunkIcon = BUILT_IN_CATEGORIES.map { it.copy(iconKey = "ic_WRONG") }

        val merged = canonicalXmbCategories(storedWithJunkIcon, XMBViewModel.FALLBACK_CATEGORIES)

        assertEquals(BUILT_IN_CATEGORIES.size, merged.size)
        merged.forEach { category ->
            val canonical = BUILT_IN_CATEGORIES.first { it.id == category.id }
            assertEquals("${category.id} lost its canonical icon", canonical.iconKey, category.iconKey)
        }
    }

    @Test
    fun `an unrecognised category really would keep the database's icon`() {
        // The control for the test above. Without it, that assertion would pass against a merge
        // that simply overwrote every icon regardless of whether the id was recognised.
        val stranger = BUILT_IN_CATEGORIES.first().copy(id = "not_a_builtin", iconKey = "ic_WRONG")

        val merged = canonicalXmbCategories(listOf(stranger), XMBViewModel.FALLBACK_CATEGORIES)

        assertEquals("ic_WRONG", merged.first { it.id == "not_a_builtin" }.iconKey)
    }

    @Test
    fun `ids and positions are unique, so the bar cannot draw two of anything`() {
        val ids = BUILT_IN_CATEGORIES.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        val positions = BUILT_IN_CATEGORIES.map { it.position }
        assertEquals(positions.size, positions.distinct().size)
    }

    @Test
    fun `the defaults are an ORDER, not a set of reserved numbers`() {
        // This used to assert that Library sat past every other built-in, so an older database
        // could gain it without colliding with a position one of its rows already held. That rule
        // made the default order unchangeable: every column added after the first release had to
        // go on the end, whatever the bar should actually read like.
        //
        // Collisions are handled where they happen instead — reconcileBuiltInCategories appends a
        // NEW built-in past whatever the database already holds and ignores the constant's number,
        // because on an established install that number is the fresh-install order and the user
        // has arranged their own. So the only thing left to assert here is what the order IS.
        assertEquals(
            listOf(
                BuiltInCategory.RECENTLY_PLAYED,
                BuiltInCategory.GAMES,
                "music",
                "videos",
                "photos",
                BuiltInCategory.LIBRARY,
                "network",
                BuiltInCategory.SETTINGS,
            ),
            BUILT_IN_CATEGORIES.sortedBy { it.position }.map { it.id },
        )
    }

    @Test
    fun `Settings is last, with room before it for a category the user makes`() {
        val settings = BUILT_IN_CATEGORIES.first { it.id == BuiltInCategory.SETTINGS }
        val others = BUILT_IN_CATEGORIES.filter { it.id != BuiltInCategory.SETTINGS }
        assertTrue("Settings is not last", others.all { it.position < settings.position })
        assertTrue(
            "no room between the last built-in and Settings for a custom category",
            settings.position > (others.maxOf { it.position } + 1),
        )
    }

    @Test
    fun `Last Played sits immediately left of Game`() {
        // "What I was doing" is one step off the column you already live in. Immediately left,
        // not merely somewhere left: a gap would let a future built-in land between them.
        val recent = BUILT_IN_CATEGORIES.first { it.id == BuiltInCategory.RECENTLY_PLAYED }
        val games = BUILT_IN_CATEGORIES.first { it.id == BuiltInCategory.GAMES }
        assertEquals(
            "Last Played must be the row immediately left of Game",
            games.position - 1,
            recent.position,
        )
    }

    @Test
    fun `no two built-ins share a position`() {
        // The failure the rules above exist to prevent. A duplicate would make the bar's order
        // depend on list order alone, which nothing else in the app promises to preserve.
        val positions = BUILT_IN_CATEGORIES.map { it.position }
        assertEquals("two built-ins share a position", positions.size, positions.toSet().size)
    }
}
