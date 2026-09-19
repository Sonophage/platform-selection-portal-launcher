package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.feature.settings.ui.SETTINGS_SCREEN_ROUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structure tests for the Settings hierarchy (docs/plans/README.md (Settings hierarchy)): the root
 * list, every L1 section's L2 rows, route resolution, and migration compatibility. Back behavior
 * itself is exercised through the shared drill plumbing (same path as Music/Video/Photo/Social).
 */
class SettingsHierarchyTest {

    // ── Root ─────────────────────────────────────────────────────────────────

    @Test fun `root is Android Settings followed by the sections in order`() {
        assertEquals(
            listOf(
                "settings_android_system",
                "settings_section_library",
                "settings_section_emulators",
                "settings_section_appearance",
                "settings_section_interface",
                "settings_section_media",
                "settings_section_system",
            ),
            XMBViewModel.SETTINGS_ROOT_ITEMS.map { it.id },
        )
    }

    @Test fun `section ids never collide with screen routes`() {
        // The select handler treats any id that resolves to a SettingsSection as a flyout drill
        // and everything else as a screen overlay — a collision would strand a screen route.
        SettingsSection.entries.forEach { section ->
            assertFalse("Section id must not be a screen route: ${section.id}", section.id in SETTINGS_SCREEN_ROUTES)
        }
    }

    // ── Section contents ─────────────────────────────────────────────────────

    @Test fun `each section exposes its L2 rows in the planned order`() {
        assertEquals(
            listOf("settings_library", "settings_windows_games", "settings_collections", "settings_artwork", "settings_artwork_sources", "settings_app_visibility"),
            settingsSectionItems(SettingsSection.LIBRARY).map { it.id },
        )
        assertEquals(
            listOf("settings_music", "settings_video", "settings_photo", "settings_books"),
            settingsSectionItems(SettingsSection.MEDIA).map { it.id },
        )
        assertEquals(
            listOf(
                "settings_emulators_installed",
                "settings_emulators_custom",
                "settings_emulators_retroarch",
                "settings_emulators_assign",
            ),
            settingsSectionItems(SettingsSection.EMULATORS).map { it.id },
        )
        // Appearance owns everything visual. Display used to hold eight unrelated groups, so its
        // parts are now separate entry points into the same screen.
        assertEquals(
            listOf("settings_themes", "settings_appearance", "settings_layout", "settings_boot"),
            settingsSectionItems(SettingsSection.APPEARANCE).map { it.id },
        )
        assertEquals(
            listOf("settings_audio", "settings_categories", "settings_controller", "settings_touch"),
            settingsSectionItems(SettingsSection.INTERFACE).map { it.id },
        )
        assertEquals(
            listOf("settings_about", "settings_logs", "settings_backup", "settings_performance", "settings_initial_setup", "settings_credits"),
            settingsSectionItems(SettingsSection.SYSTEM).map { it.id },
        )
    }

    @Test fun `every L2 row id resolves to a settings screen route`() {
        SettingsSection.entries
            .flatMap { settingsSectionItems(it) }
            .forEach { row ->
                assertTrue("No route for L2 row ${row.id}", row.id in SETTINGS_SCREEN_ROUTES)
            }
    }

    @Test fun `L2 row ids are unique inside their section`() {
        SettingsSection.entries.forEach { section ->
            val ids = settingsSectionItems(section).map { it.id }
            assertEquals("Duplicate ids in ${section.id}", ids, ids.distinct())
        }
    }

    @Test fun `every section row carries a title and subtitle`() {
        (XMBViewModel.SETTINGS_ROOT_ITEMS + SettingsSection.entries.flatMap { settingsSectionItems(it) })
            .forEach { row ->
                assertTrue("Missing title for ${row.id}", !row.title.isNullOrBlank())
                assertTrue("Missing subtitle for ${row.id}", !row.subtitle.isNullOrBlank())
            }
    }

    // ── Migration compatibility ──────────────────────────────────────────────

    @Test fun `every legacy flat settings row remains a resolvable route`() {
        // Direct callers (setup prompts, context menus, achievements links, first-run wizard) still
        // assign these ids to activeSettingsScreen — they must keep resolving in SettingsNavHost.
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
        val libraryIds = settingsSectionItems(SettingsSection.LIBRARY).map { it.id }
        assertTrue("Hidden Games missing from Library", libraryIds.contains("settings_app_visibility"))
        assertTrue("settings_app_visibility route missing", SETTINGS_SCREEN_ROUTES.contains("settings_app_visibility"))
    }

    @Test fun `Update Achievements route is present and distinct`() {
    }

    @Test fun `Hidden Games is not reachable from Display settings`() {
        // Display keeps no link: its only remaining ids are the combined screen routes, none of
        // which is the app-visibility route.
        val interfaceIds = settingsSectionItems(SettingsSection.INTERFACE).map { it.id }
        assertFalse(interfaceIds.contains("settings_app_visibility"))
    }

    // ── Audio screen ─────────────────────────────────────────────────────────

    @Test fun `Audio is present under Interface via its own route`() {
        val interfaceIds = settingsSectionItems(SettingsSection.INTERFACE).map { it.id }
        assertTrue("Sound missing from Interface", interfaceIds.contains("settings_audio"))
        assertTrue("settings_audio route missing", SETTINGS_SCREEN_ROUTES.contains("settings_audio"))
    }

    @Test fun `the Interface audio row is titled Sound with a menu-and-boot subtitle`() {
        // Phase 3 of the seven-sound work (docs/plans/README.md C10): the screen is renamed Audio → Sound and now owns the
        // boot sound too. The route id deliberately stays settings_audio — renaming it would
        // break cursor restore and every focus key under it.
        val row = settingsSectionItems(SettingsSection.INTERFACE).first { it.id == "settings_audio" }
        assertEquals("Sound", row.title)
        assertEquals("Menu & boot sounds", row.subtitle)
    }
}
