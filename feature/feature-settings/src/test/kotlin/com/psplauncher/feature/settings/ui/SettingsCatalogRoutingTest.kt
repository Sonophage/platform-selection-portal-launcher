package com.psplauncher.feature.settings.ui

import com.psplauncher.core.domain.model.SETTINGS_CATALOG
import com.psplauncher.core.domain.model.SettingsSectionId
import com.psplauncher.core.domain.model.settingsEntriesIn
import com.psplauncher.core.domain.model.settingsSectionFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsCatalogRoutingTest {
    @Test
    fun `every screen the rail offers can actually be opened`() {
        val unroutable = SETTINGS_CATALOG.map { it.id }.filterNot { it in SETTINGS_SCREEN_ROUTES }
        assertEquals(
            emptyList(),
            unroutable,
            "the rail would show these and do nothing on confirm; add them to " +
                "SETTINGS_SCREEN_ROUTES and the when in SettingsNavHost",
        )
    }

    @Test
    fun `every section has screens, and every screen belongs to exactly one`() {
        SettingsSectionId.entries.forEach { section ->
            assertTrue(
                settingsEntriesIn(section).isNotEmpty(),
                "$section is a crossbar row that drills into an empty list",
            )
        }
        val ids = SETTINGS_CATALOG.map { it.id }
        assertEquals(ids.size, ids.distinct().size, "a screen id appears twice in the catalog")
    }

    @Test
    fun `a section id is never also a screen id`() {
        val sectionIds = SettingsSectionId.entries.map { it.id }.toSet()
        assertEquals(
            emptySet(),
            SETTINGS_CATALOG.map { it.id }.toSet() intersect sectionIds,
        )
    }

    @Test
    fun `a route outside the catalog has no section, so it gets no rail`() {
        assertTrue("settings_initial_setup_first" in SETTINGS_SCREEN_ROUTES)
        assertNull(settingsSectionFor("settings_initial_setup_first"))
        assertNull(settingsSectionFor("settings_import_pc"))

        assertNotNull(settingsSectionFor("settings_artwork"))
    }
}
