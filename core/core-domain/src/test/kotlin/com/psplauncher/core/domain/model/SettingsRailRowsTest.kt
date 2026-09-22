package com.psplauncher.core.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The section rail's shape, and the shoulders that are now the only way between sections.
 *
 * This asserted a two-level rail — every section listed, the open one expanded under it. That rail
 * was thirteen rows and showed the open section twice, once as its heading and once as its first
 * screen. It is one section's screens now, with the section's name as the page title.
 *
 * The pair that matters: the rail no longer offers a route to another section, so
 * [settingsSectionStepTarget] is the only one. A rail that listed everything could afford a broken
 * step; this one cannot, and the last two tests here are why.
 */
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
        // The wizard's first-run variant and Library Manager's deep links are finished screens
        // reached from elsewhere; a rail there is a way out of something meant to be completed.
        assertEquals(emptyList(), settingsRailRows("settings_initial_setup_first"))
        assertEquals(emptyList(), settingsRailRows("settings_import_pc"))
        assertEquals(emptyList(), settingsRailRows(null))
    }

    @Test
    fun `the shoulders reach every section from every screen`() {
        // The rail cannot leave a section any more, so this is the whole escape route. Stepping
        // forward from any screen, section by section, must visit all seven.
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
        // settingsSectionStepTarget takes the section's first screen. An empty section would make
        // the shoulders silently do nothing on the way past it.
        SettingsSectionId.entries.forEach { section ->
            assertTrue(settingsEntriesIn(section).isNotEmpty(), "$section has no screens")
        }
    }
}
