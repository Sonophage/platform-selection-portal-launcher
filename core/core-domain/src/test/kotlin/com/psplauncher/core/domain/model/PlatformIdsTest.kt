package com.psplauncher.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the value of the ids the code branches on.
 *
 * [PlatformIds.WINDOWS] had nine separate definitions across five modules before this object
 * existed, and the strings they stored are also written into the database by the seeder and read
 * back by the scanner. So this is not a tautology: changing the constant would silently orphan
 * every imported PC game, and the literal here is the record of what is already on disk.
 */
class PlatformIdsTest {

    @Test
    fun `the windows id is the one already written to every install's database`() {
        assertEquals("windows", PlatformIds.WINDOWS)
    }
}
