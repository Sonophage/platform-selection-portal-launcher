package com.psplauncher.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The section rail's two-level shape.
 *
 * It is the only place the settings tree exists now: the crossbar is down to a single Settings
 * row, so a rail that showed the wrong section expanded, or no sections at all, would leave whole
 * parts of Settings unreachable without anything failing.
 */
class SettingsRailRowsTest {

    @Test
    fun `every section is listed, whichever screen you are on`() {
        SETTINGS_CATALOG.forEach { entry ->
            val sections = settingsRailRows(entry.id).filter { it.isSection }.map { it.id }
            assertEquals(
                SettingsSectionId.entries.map { it.id },
                sections,
                "on ${entry.id} the rail must still offer every section",
            )
        }
    }

    @Test
    fun `exactly the section you are in is expanded`() {
        val rows = settingsRailRows("settings_audio")
        val screens = rows.filterNot { it.isSection }.map { it.id }
        assertEquals(settingsEntriesIn(SettingsSectionId.INTERFACE).map { it.id }, screens)
        // ...and nothing from any other section leaked in.
        assertFalse(rows.any { it.id == "settings_artwork" })
    }

    @Test
    fun `a section's screens sit directly under its own row`() {
        // Indentation is all that says which section a screen belongs to, so the ORDER has to
        // carry it: a screen listed under the wrong heading reads as belonging to it.
        val rows = settingsRailRows("settings_themes")
        val headingIndex = rows.indexOfFirst { it.id == SettingsSectionId.APPEARANCE.id }
        val expected = settingsEntriesIn(SettingsSectionId.APPEARANCE).map { it.id }
        assertEquals(expected, rows.subList(headingIndex + 1, headingIndex + 1 + expected.size).map { it.id })
    }

    @Test
    fun `a section row opens its first screen`() {
        settingsRailRows("settings_about").filter { it.isSection }.forEach { row ->
            val section = SettingsSectionId.entries.first { it.id == row.id }
            assertEquals(settingsEntriesIn(section).first().id, row.opens)
        }
    }

    @Test
    fun `a row's id and what it opens differ only for a section`() {
        settingsRailRows("settings_library").forEach { row ->
            if (row.isSection) {
                // The LIBRARY heading and the Library Manager screen both open settings_library.
                // They must not also share an id, or the rail would mark two rows as current.
                assertTrue(row.id != row.opens, "${row.id} must not be its own target")
            } else {
                assertEquals(row.id, row.opens)
            }
        }
    }

    @Test
    fun `every row id is unique, so the list can be keyed by it`() {
        SETTINGS_CATALOG.forEach { entry ->
            val ids = settingsRailRows(entry.id).map { it.id }
            assertEquals(ids.size, ids.distinct().size, "duplicate rail id while on ${entry.id}")
        }
    }

    @Test
    fun `a route outside the catalog gets no rail at all`() {
        // The wizard's first-run variant is meant to be finished, not navigated away from.
        assertEquals(emptyList(), settingsRailRows("settings_initial_setup_first"))
        assertEquals(emptyList(), settingsRailRows("settings_import_pc"))
        assertEquals(emptyList(), settingsRailRows(null))
    }

    @Test
    fun `the cursor can always find the screen you are on`() {
        // The scaffold opens the rail with its cursor on indexOfFirst { it.id == screenId }. A
        // screen missing from its own rail would silently park the cursor on row zero.
        SETTINGS_CATALOG.forEach { entry ->
            assertTrue(
                settingsRailRows(entry.id).any { it.id == entry.id && !it.isSection },
                "${entry.id} is absent from its own rail",
            )
        }
    }
}
