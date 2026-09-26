package com.psplauncher.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SettingsRailRowsTest {
    @Test
    fun `the rail is exactly its own section's screens`() {
        SETTINGS_CATALOG.forEach { entry ->
            assertEquals(
                settingsEntriesIn(entry.section).map { it.id },
                settingsRailRows(entry.id).map { it.id },
                "rail for ${entry.id}",
            )
        }
    }

    @Test
    fun `the screen you are on is always in its own rail, exactly once`() {
        SETTINGS_CATALOG.forEach { entry ->
            val ids = settingsRailRows(entry.id).map { it.id }
            assertEquals(1, ids.count { it == entry.id }, "${entry.id} in its own rail")
        }
    }

    @Test
    fun `no row is repeated`() {
        SETTINGS_CATALOG.forEach { entry ->
            val ids = settingsRailRows(entry.id).map { it.id }
            assertEquals(ids.distinct(), ids, "duplicate rail row for ${entry.id}")
        }
    }

    @Test
    fun `a route outside the catalog has no rail`() {
        assertEquals(emptyList(), settingsRailRows("settings_initial_setup_first"))
        assertEquals(emptyList(), settingsRailRows("settings_import_pc"))
        assertEquals(emptyList(), settingsRailRows(null))
    }

    @Test
    fun `the shoulders reach every section from every screen`() {
        SETTINGS_CATALOG.forEach { entry ->
            val seen = mutableSetOf<SettingsSectionId>()
            var id: String? = entry.id
            repeat(SettingsSectionId.entries.size) {
                val section = id?.let(::settingsSectionFor)
                assertNotNull(section, "step from ${entry.id} left the catalog")
                seen += section
                id = settingsSectionStepTarget(id, 1)
            }
            assertEquals(SettingsSectionId.entries.toSet(), seen, "from ${entry.id}")
        }
    }

    @Test
    fun `stepping wraps in both directions and always lands on a real screen`() {
        val first = SettingsSectionId.entries.first()
        val last = SettingsSectionId.entries.last()
        assertEquals(last, settingsSectionStep(first, -1))
        assertEquals(first, settingsSectionStep(last, +1))

        SETTINGS_CATALOG.forEach { entry ->
            listOf(-1, +1).forEach { delta ->
                val target = settingsSectionStepTarget(entry.id, delta)
                assertNotNull(target, "no target stepping $delta from ${entry.id}")
                assertNotNull(settingsEntryFor(target), "$target is not a catalog screen")
            }
        }
    }

    @Test
    fun `every section has at least one screen to land on`() {
        SettingsSectionId.entries.forEach { section ->
            assertTrue(settingsEntriesIn(section).isNotEmpty(), "$section has no screens")
        }
    }
}
