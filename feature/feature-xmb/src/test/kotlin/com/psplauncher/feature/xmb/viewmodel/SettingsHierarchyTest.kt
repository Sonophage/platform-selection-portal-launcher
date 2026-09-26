package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.SettingsSectionId
import com.psplauncher.core.domain.model.SETTINGS_ROOT_SCREEN_ID
import com.psplauncher.core.domain.model.settingsEntriesIn
import com.psplauncher.core.domain.model.settingsEntryFor
import com.psplauncher.core.domain.model.settingsRailRows
import com.psplauncher.feature.settings.ui.SETTINGS_SCREEN_ROUTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsHierarchyTest {
    @Test fun `only the wizard leaves a return address for Back`() {
        assertEquals(
            XMBViewModel.INITIAL_SETUP_SCREEN_ID,
            XMBViewModel.returnAddressFor(XMBViewModel.INITIAL_SETUP_SCREEN_ID),
        )
        assertEquals(
            XMBViewModel.INITIAL_SETUP_FIRST_RUN_SCREEN_ID,
            XMBViewModel.returnAddressFor(XMBViewModel.INITIAL_SETUP_FIRST_RUN_SCREEN_ID),
        )
        assertEquals(null, XMBViewModel.returnAddressFor("settings_themes"))
        assertEquals(null, XMBViewModel.returnAddressFor(SETTINGS_ROOT_SCREEN_ID))
        assertEquals(null, XMBViewModel.returnAddressFor(null))
    }

    @Test fun `both wizard routes are real screens, and the id list covers both`() {
        XMBViewModel.WIZARD_SCREEN_IDS.forEach {
            assertTrue("$it has no route", it in SETTINGS_SCREEN_ROUTES)
        }
        assertTrue(XMBViewModel.INITIAL_SETUP_SCREEN_ID in XMBViewModel.WIZARD_SCREEN_IDS)
        assertTrue(XMBViewModel.INITIAL_SETUP_FIRST_RUN_SCREEN_ID in XMBViewModel.WIZARD_SCREEN_IDS)
    }

    @Test fun `only the re-run wizard route is in the catalog, which is what Skip depends on`() {
        assertEquals(
            null,
            settingsEntryFor(XMBViewModel.INITIAL_SETUP_FIRST_RUN_SCREEN_ID),
        )
        assertTrue(settingsEntryFor(XMBViewModel.INITIAL_SETUP_SCREEN_ID) != null)
    }

    @Test fun `the crossbar column is two rows, open settings and open Android's`() {
        assertEquals(
            listOf(XMBViewModel.OPEN_SETTINGS_ITEM_ID, "settings_android_system"),
            XMBViewModel.SETTINGS_ROOT_ITEMS.map { it.id },
        )
    }

    @Test fun `the settings row opens the root, and the root is reachable and railless`() {
        val opensId = SETTINGS_ROOT_SCREEN_ID
        assertTrue("The settings row opens $opensId, which has no route", opensId in SETTINGS_SCREEN_ROUTES)

        assertEquals(
            "The root must not be one of the catalog's screens",
            null,
            com.psplauncher.core.domain.model.settingsEntryFor(opensId),
        )
        assertTrue("The root must have no rail", settingsRailRows(opensId).isEmpty())
    }

    @Test fun `every section on the root list opens a real screen`() {
        SettingsSectionId.entries.forEach { section ->
            val opens = settingsEntriesIn(section).firstOrNull()?.id
            assertTrue("$section has no screen to open", opens != null)
            assertTrue("$section opens $opens, which has no route", opens in SETTINGS_SCREEN_ROUTES)
        }
    }

    @Test fun `every crossbar row carries a title and a subtitle`() {
        XMBViewModel.SETTINGS_ROOT_ITEMS.forEach { row ->
            assertTrue("Missing title for ${row.id}", !row.title.isNullOrBlank())
            assertTrue("Missing subtitle for ${row.id}", !row.subtitle.isNullOrBlank())
        }
    }

    @Test fun `Android Settings is not a PFP screen route`() {
        assertFalse("settings_android_system" in SETTINGS_SCREEN_ROUTES)
    }

    @Test fun `each section exposes its screens in the planned order`() {
        assertEquals(

            listOf("settings_library", "settings_artwork", "settings_artwork_sources", "settings_app_visibility"),
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

        assertEquals(
            listOf("settings_themes", "settings_appearance", "settings_layout", "settings_boot"),
            settingsEntriesIn(SettingsSectionId.APPEARANCE).map { it.id },
        )
        assertEquals(
            listOf("settings_audio", "settings_categories", "settings_controller", "settings_touch", "settings_performance"),
            settingsEntriesIn(SettingsSectionId.INTERFACE).map { it.id },
        )
        assertEquals(

            listOf("settings_initial_setup", "settings_about", "settings_logs", "settings_backup", "settings_credits"),
            settingsEntriesIn(SettingsSectionId.SYSTEM).map { it.id },
        )
    }

    @Test fun `every screen the rail can reach resolves to a route`() {
        SettingsSectionId.entries.forEach { section ->
            settingsRailRows(settingsEntriesIn(section).first().id).forEach { row ->
                assertTrue("No route for rail row ${row.id}", row.id in SETTINGS_SCREEN_ROUTES)
            }
        }
    }

    @Test fun `every section the shoulders can reach resolves to a route`() {
        SettingsSectionId.entries.forEach { section ->
            val from = settingsEntriesIn(section).first().id
            listOf(-1, +1).forEach { delta ->
                val target = com.psplauncher.core.domain.model.settingsSectionStepTarget(from, delta)
                assertTrue("No route stepping $delta from $from (got $target)", target in SETTINGS_SCREEN_ROUTES)
            }
        }
    }

    @Test fun `section ids never collide with screen routes`() {
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

    @Test fun `every legacy flat settings row remains a resolvable route`() {
        listOf(
            "settings_library", "settings_import_pc", "settings_music", "settings_video",
            "settings_photo", "settings_categories", "settings_artwork",
            "settings_artwork_import", "settings_emulators",
            "settings_themes", "settings_display", "settings_controller", "settings_backup",
            "settings_logs", "settings_about", "settings_credits",
            "settings_initial_setup", "settings_initial_setup_first",
        ).forEach { id ->
            assertTrue("Legacy route dropped: $id", id in SETTINGS_SCREEN_ROUTES)
        }
    }

    @Test fun `Hidden Games is present under Library via its dedicated route`() {
        val libraryIds = settingsEntriesIn(SettingsSectionId.LIBRARY).map { it.id }
        assertTrue("Hidden Games missing from Library", libraryIds.contains("settings_app_visibility"))
        assertTrue("settings_app_visibility route missing", SETTINGS_SCREEN_ROUTES.contains("settings_app_visibility"))
    }

    @Test fun `Hidden Games is not reachable from Interface settings`() {
        val interfaceIds = settingsEntriesIn(SettingsSectionId.INTERFACE).map { it.id }
        assertFalse(interfaceIds.contains("settings_app_visibility"))
    }

    @Test fun `Sound is present under Interface via its own route`() {
        val interfaceIds = settingsEntriesIn(SettingsSectionId.INTERFACE).map { it.id }
        assertTrue("Sound missing from Interface", interfaceIds.contains("settings_audio"))
        assertTrue("settings_audio route missing", SETTINGS_SCREEN_ROUTES.contains("settings_audio"))
    }

    @Test fun `the Interface audio row is titled Sound with a menu-and-boot subtitle`() {
        val row = settingsEntriesIn(SettingsSectionId.INTERFACE).first { it.id == "settings_audio" }
        assertEquals("Sound", row.title)
        assertEquals("Menu & boot sounds", row.subtitle)
    }
}
