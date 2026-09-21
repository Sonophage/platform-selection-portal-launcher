package com.psplauncher.feature.settings.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The in-progress contents of the credential fields, as a value rather than six `remember`s.
 *
 * Why this moved out of the screen at all: on a 462dp-tall display the keyboard covers everything
 * below the field being filled, so entering a two-part credential means dismissing it and
 * scrolling to reach the second box. Any back press that left the pane destroyed the composition
 * and the half-typed pair with it, and three of the six were additionally keyed on the stored
 * value, so a store emission could blank the field mid-entry.
 *
 * What is worth testing is the get/with pair. It is a six-armed `when` written twice, facing
 * itself: a single mismatched arm reads one field and writes another, which on this screen means
 * typing a client secret into the box and saving the client id. That is invisible by inspection
 * and the sort of thing a table walks in one line.
 */
class CredentialDraftsTest {

    @Test
    fun `every field round-trips through its own slot`() {
        for (field in CredentialField.entries) {
            val written = CredentialDrafts().with(field, "value-of-${field.name}")
            assertEquals("$field reads back what was written", "value-of-${field.name}", written[field])
        }
    }

    @Test
    fun `writing one field leaves the other five alone`() {
        for (field in CredentialField.entries) {
            // Start from a state where every slot is distinguishable, so a mis-wired arm shows up
            // as a value landing in the wrong place rather than as a blank.
            val seeded = CredentialField.entries.fold(CredentialDrafts()) { d, f -> d.with(f, f.name) }
            val after = seeded.with(field, "CHANGED")
            for (other in CredentialField.entries.filter { it != field }) {
                assertEquals("writing $field disturbed $other", other.name, after[other])
            }
        }
    }

    @Test
    fun `a fresh set of drafts is empty, so no save row can appear on stale text`() {
        val fresh = CredentialDrafts()
        for (field in CredentialField.entries) {
            assertEquals("$field starts empty", "", fresh[field])
        }
    }
}
