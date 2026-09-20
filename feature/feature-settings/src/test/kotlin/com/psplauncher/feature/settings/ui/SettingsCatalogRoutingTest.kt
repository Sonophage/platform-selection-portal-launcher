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

/**
 * The settings catalog and the nav host's route table are a pair: the catalog says what the
 * section rail offers, the route table says what `SettingsNavHost` can actually open. An entry
 * in one and not the other is a rail row that does nothing when you confirm it, and nothing
 * about that failure would name the catalog as the cause.
 *
 * The route table is deliberately the LARGER set — deep links, the wizard's first-run variant,
 * and Library Manager's pre-opened cards are all routes you reach from elsewhere and never from
 * a sibling list. So the direction that matters is one-way, and it is asserted as such rather
 * than as equality.
 */
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
        // The crossbar's select handler routes a section id to the flyout and anything else to
        // activeSettingsScreen. One string doing both would pick a branch by accident.
        val sectionIds = SettingsSectionId.entries.map { it.id }.toSet()
        assertEquals(
            emptySet(),
            SETTINGS_CATALOG.map { it.id }.toSet() intersect sectionIds,
        )
    }

    @Test
    fun `a route outside the catalog has no section, so it gets no rail`() {
        // The wizard's first-run variant is reachable and deliberately absent from the catalog:
        // it has no siblings to move between, and a rail there would offer a way out of a screen
        // that is meant to be finished.
        assertTrue("settings_initial_setup_first" in SETTINGS_SCREEN_ROUTES)
        assertNull(settingsSectionFor("settings_initial_setup_first"))
        assertNull(settingsSectionFor("settings_import_pc"))
        // ...while an ordinary screen does get one.
        assertNotNull(settingsSectionFor("settings_artwork"))
    }
}
