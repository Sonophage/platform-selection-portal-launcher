package com.psplauncher.core.data.database.seeder

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The Android Memory Card is seeded once, ever, and then it is the user's to delete.
 *
 * It used to be created from LibraryManagerViewModel's init block, which meant deleting it and
 * reopening Library Manager brought it straight back — reported from the device. The delete was
 * correct; the creator undid it. The same shape as the category delete that a restore undoes.
 */
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
        // The bug, stated as an assertion: seeded once, no card now, and the answer is still no.
        assertEquals(
            AndroidCardSeed.NOTHING,
            androidCardSeedAction(alreadySeeded = true, cardExists = false),
        )
    }

    @Test
    fun `an install that already had the card gets the flag, not a second card`() {
        // The subtle half. Every existing install reaches this change WITH a card and WITHOUT the
        // flag. Returning NOTHING here would leave the flag unwritten, the one-shot would never
        // retire, and the card would come back the first time the user deleted it — the reported
        // bug surviving its own fix.
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
