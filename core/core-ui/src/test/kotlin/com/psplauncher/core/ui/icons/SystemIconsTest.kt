package com.psplauncher.core.ui.icons

import com.psplauncher.core.ui.R
import com.psplauncher.themekit.SYSICON_PLATFORM_IDS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SystemIconsTest {
    @Test
    fun `platform id list is non-empty and unique`() {
        assertTrue(SYSICON_PLATFORM_IDS.isNotEmpty())
        assertEquals(
            SYSICON_PLATFORM_IDS.size,
            SYSICON_PLATFORM_IDS.toSet().size,
            "duplicate platform id in SYSICON_PLATFORM_IDS",
        )
    }

    @Test
    fun `every registered platform id resolves to dedicated art, not the fallback`() {
        for (id in SYSICON_PLATFORM_IDS) {
            val res = systemIconRes(id)
            assertNotEquals(
                R.drawable.sysicon_default,
                res,
                "platform '$id' resolves to sysicon_default — add a when branch or remove it from the registry",
            )
        }
    }

    @Test
    fun `ids are matched case-insensitively`() {
        for (id in SYSICON_PLATFORM_IDS) {
            assertEquals(
                systemIconRes(id),
                systemIconRes(id.uppercase()),
                "lookup must lowercase before matching: $id",
            )
        }
    }

    @Test
    fun `unregistered ids fall back to the default art`() {
        assertEquals(R.drawable.sysicon_default, systemIconRes(null))
        assertEquals(R.drawable.sysicon_default, systemIconRes("not_a_platform"))
        assertEquals(R.drawable.sysicon_default, systemIconRes("default"))
    }
}
