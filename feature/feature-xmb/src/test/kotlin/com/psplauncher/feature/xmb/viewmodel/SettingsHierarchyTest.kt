package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.SettingsSectionId
import com.psplauncher.core.domain.model.settingsEntriesIn
import com.psplauncher.core.domain.model.settingsRailRows
import com.psplauncher.feature.settings.ui.SETTINGS_SCREEN_ROUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Settings tree as the crossbar and the section rail present it: the crossbar's two root rows,
 * every section's screens in order, and route resolution for all of them.
 *
 * The crossbar used to own this tree — six section rows that drilled into a two-pane flyout. It is
 * one row now, and the rail inside the settings screens is where the tree lives, so the tests that
 * described the flyout describe the rail instead. What has not changed is the thing worth
 * guarding: a row the user can reach whose id no route resolves is a dead end that reports nothing.
 *
 * This lives in feature-xmb rather than beside the catalog in core-domain because the route table
 * it checks against is feature-settings', and this module is the one that can see both.
 */
class SettingsHierarchyTest {

    // ── Crossbar root ────────────────────────────────────────────────────────

    @Test fun `the crossbar offers one Settings row and one Android Settings row`() {
        assertEquals(
            listOf("settings_library", "settings_android_system"),
            XMBViewModel.SETTINGS_ROOT_ITEMS.map { it.id },
        )
        assertEquals(
            listOf("Settings", "Android Settings"),
            XMBViewModel.SETTINGS_ROOT_ITEMS.map { it.title },
        )
    }

    @Test fun `the Settings row opens the first screen of the first section`() {
        // Not a hand-written id: reordering the catalog has to move this with it, or the crossbar
        // would open a screen whose section is no longer the one the rail opens expanded.
        assertEquals(
            settingsEntriesIn(SettingsSectionId.entries.first()).first().id,
            XMBViewModel.SETTINGS_ENTRY_SCREEN_ID,
        )
        assertEquals(XMBViewModel.SETTINGS_ENTRY_SCREEN_ID, XMBViewModel.SETTINGS_ROOT_ITEMS.first().id)
        assertTrue(XMBViewModel.SETTINGS_ENTRY_SCREEN_ID in SETTINGS_SCREEN_ROUTES)
    }

    @Test fun `every crossbar row carries a title and a subtitle`() {
        XMBViewModel.SETTINGS_ROOT_ITEMS.forEach { row ->
            assertTrue("Missing title for ${row.id}", !row.title.isNullOrBlank())
            assertTrue("Missing subtitle for ${row.id}", !row.subtitle.isNullOrBlank())
        }
    }

    @Test fun `Android Settings is not a PFP screen route`() {
        // It opens the device's own settings app through an intent. A route of the same name
        // would make the select handler open a PFP screen instead, silently.
        assertFalse("settings_android_system" in SETTINGS_SCREEN_ROUTES)
    }

    // ── Section contents ─────────────────────────────────────────────────────

    @Test fun `each section exposes its screens in the planned order`() {
        assertEquals(
            listOf("settings_library", "settings_windows_games", "settings_collections", "settings_artwork", "settings_artwork_sources", "settings_app_visibility"),
            settingsEntriesIn(SettingsSectionId.LIBRARY).map { it.id },
        )
        assertEquals(
            listOf("settings_music", "settings_video", "settings_photo", "settings_books"),
            settingsEntriesIn(SettingsSectionId.MEDIA).map { it.id },
        )
        assertEquals(
            listOf(
                "settings_emulators_installed",
                "settings_emulators_custom",
                "settings_emulators_retroarch",
                "settings_emulators_assign",
            ),
            settingsEntriesIn(SettingsSectionId.EMULATORS).map { it.id },
        )
        // Appearance owns everything visual. Display used to hold eight unrelated groups, so its
        // parts are now separate entry points into the same screen.
        assertEquals(
            listOf("settings_themes", "settings_appearance", "settings_layout", "settings_boot"),
            settingsEntriesIn(SettingsSectionId.APPEARANCE).map { it.id },
        )
        assertEquals(
            listOf("settings_audio", "settings_categories", "settings_controller", "settings_touch"),
            settingsEntriesIn(SettingsSectionId.INTERFACE).map { it.id },
        )
        assertEquals(
            listOf("settings_about", "settings_logs", "settings_backup", "settings_performance", "settings_initial_setup", "settings_credits"),
            settingsEntriesIn(SettingsSectionId.SYSTEM).map { it.id },
        )
    }

    @Test fun `every screen the rail can reach resolves to a route`() {
        // The rail is now the ONLY way to reach most of these: the crossbar opens one screen and
        // the rail gets you to the rest. A row here with no route is a dead end with no symptom.
        SettingsSectionId.entries.forEach { section ->
            settingsRailRows(settingsEntriesIn(section).first().id).forEach { row ->
                assertTrue("No route for rail row ${row.id} (opens ${row.opens})", row.opens in SETTINGS_SCREEN_ROUTES)
            }
        }
    }

    @Test fun `section ids never collide with screen routes`() {
        // The rail draws a section row and its screens in one list. One id meaning both would
        // make the rail highlight two rows as "where you are".
        SettingsSectionId.entries.forEach { section ->
            assertFalse("Section id must not be a screen route: ${section.id}", section.id in SETTINGS_SCREEN_ROUTES)
        }
    }

    @Test fun `screen ids are unique inside their section`() {
        SettingsSectionId.entries.forEach { section ->
            val ids = settingsEntriesIn(section).map { it.id }
            assertEquals("Duplicate ids in ${section.id}", ids, ids.distinct())
        }
    }

    // ── Migration compatibility ──────────────────────────────────────────────

    @Test fun `every legacy flat settings row remains a resolvable route`() {
        // Direct callers (setup prompts, context menus, first-run wizard) still assign these ids
        // to activeSettingsScreen — they must keep resolving in SettingsNavHost.
        listOf(
            "settings_library", "settings_import_pc", "settings_music", "settings_video",
            "settings_photo", "settings_categories", "settings_collections", "settings_artwork",
            "settings_artwork_import", "settings_emulators",
            "settings_themes", "settings_display", "settings_controller", "settings_backup",
            "settings_logs", "settings_about", "settings_credits",
            "settings_initial_setup", "settings_initial_setup_first",
        ).forEach { id ->
            assertTrue("Legacy route dropped: $id", id in SETTINGS_SCREEN_ROUTES)
        }
    }

    // ── Hidden Games move ────────────────────────────────────────────────────

    @Test fun `Hidden Games is present under Library via its dedicated route`() {
        val libraryIds = settingsEntriesIn(SettingsSectionId.LIBRARY).map { it.id }
        assertTrue("Hidden Games missing from Library", libraryIds.contains("settings_app_visibility"))
        assertTrue("settings_app_visibility route missing", SETTINGS_SCREEN_ROUTES.contains("settings_app_visibility"))
    }

    @Test fun `Hidden Games is not reachable from Interface settings`() {
        val interfaceIds = settingsEntriesIn(SettingsSectionId.INTERFACE).map { it.id }
        assertFalse(interfaceIds.contains("settings_app_visibility"))
    }

    // ── Audio screen ─────────────────────────────────────────────────────────

    @Test fun `Sound is present under Interface via its own route`() {
        val interfaceIds = settingsEntriesIn(SettingsSectionId.INTERFACE).map { it.id }
        assertTrue("Sound missing from Interface", interfaceIds.contains("settings_audio"))
        assertTrue("settings_audio route missing", SETTINGS_SCREEN_ROUTES.contains("settings_audio"))
    }

    @Test fun `the Interface audio row is titled Sound with a menu-and-boot subtitle`() {
        // Phase 3 of the seven-sound work (docs/plans/README.md C10): the screen is renamed Audio → Sound and now owns the
        // boot sound too. The route id deliberately stays settings_audio — renaming it would
        // break cursor restore and every focus key under it.
        val row = settingsEntriesIn(SettingsSectionId.INTERFACE).first { it.id == "settings_audio" }
        assertEquals("Sound", row.title)
        assertEquals("Menu & boot sounds", row.subtitle)
    }
}
