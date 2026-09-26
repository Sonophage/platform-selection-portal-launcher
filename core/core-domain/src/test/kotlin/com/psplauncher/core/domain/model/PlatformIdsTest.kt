package com.psplauncher.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class PlatformIdsTest {
    @Test
    fun `the windows id is the one already written to every install's database`() {
        assertEquals("windows", PlatformIds.WINDOWS)
    }

    @Test
    fun `the android id is the one already written to every install's database`() {
        assertEquals("android", PlatformIds.ANDROID)
    }

    @Test
    fun `the ids are distinct`() {
        val all = listOf(PlatformIds.WINDOWS, PlatformIds.ANDROID)
        assertEquals(all.distinct(), all)
    }
}
