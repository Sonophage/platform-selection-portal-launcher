package com.psplauncher.core.data.database.seeder

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidCardSeedTest {
    @Test
    fun `a fresh install gets the card`() {
        assertEquals(
            AndroidCardSeed.CREATE_AND_MARK,
            androidCardSeedAction(alreadySeeded = false, cardExists = false),
        )
    }

    @Test
    fun `a card deleted after seeding is not put back`() {
        assertEquals(
            AndroidCardSeed.NOTHING,
            androidCardSeedAction(alreadySeeded = true, cardExists = false),
        )
    }

    @Test
    fun `an install that already had the card gets the flag, not a second card`() {
        assertEquals(
            AndroidCardSeed.MARK_ONLY,
            androidCardSeedAction(alreadySeeded = false, cardExists = true),
        )
    }

    @Test
    fun `nothing happens once the flag is set, card or no card`() {
        assertEquals(
            AndroidCardSeed.NOTHING,
            androidCardSeedAction(alreadySeeded = true, cardExists = true),
        )
    }
}
