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
    fun `Library is appended rather than inserted`() {
        // Its position is deliberately past the others so a database seeded by an older build
        // gains it without colliding with the positions its existing rows already hold.
        val library = BUILT_IN_CATEGORIES.first { it.id == BuiltInCategory.LIBRARY }
        assertTrue(
            "Library at ${library.position} would collide with an established row",
            BUILT_IN_CATEGORIES.filter { it.id != BuiltInCategory.LIBRARY }.all { it.position < library.position },
        )
    }
}
