package com.psplauncher.feature.settings.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

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
