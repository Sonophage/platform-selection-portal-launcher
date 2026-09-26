package com.psplauncher.studio.preview

import com.psplauncher.themekit.CustomizableIcons
import com.psplauncher.themekit.IconSlot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudioIconSetTest {
    @Test
    fun `every non-console customizable slot has studio art`() {
        val studioKeys = StudioIconSet.RESOURCE_SLOTS.keys + StudioIconSet.ITEM_VECTORS.keys
        val expected = CustomizableIcons.ALL
            .filter { it.group != IconSlot.Group.CONSOLE }
            .map { it.key }
        assertEquals(
            expected.sorted(),
            studioKeys.sorted(),
            "slots missing Studio art — or Studio keys with no slot — must be resolved",
        )
    }

    @Test
    fun `resource slots and vector slots do not overlap`() {
        val overlap = StudioIconSet.RESOURCE_SLOTS.keys intersect StudioIconSet.ITEM_VECTORS.keys
        assertTrue(overlap.isEmpty(), "a slot must have exactly one default, found in both maps: $overlap")
    }
}
