package com.psplauncher.feature.backup

import org.junit.Assert.assertTrue
import org.junit.Test

class BackupKeyCoverageTest {
    private val covered = BackupManager.BACKED_UP_KEY_NAMES

    private fun assertCovered(vararg keys: String) {
        keys.forEach { key ->
            assertTrue("$key is not in BackupManager's backed-up key list", key in covered)
        }
    }

    @Test
    fun `font colour and text legibility keys are backed up`() {
        assertCovered(
            "display_text_color",
            "display_text_color_exact",
            "display_text_legibility",
            "display_text_contrast_notice_suppressed",
        )
    }

    @Test
    fun `the XMB's own display switches are backed up`() {
        assertCovered("pref_xmb_game_metadata", "pref_xmb_item_backdrop")
    }

    @Test
    fun `icon and text appearance settings are backed up`() {
        assertCovered(
            "display_icon_legibility",
            "display_solid_unfocused_icons",
            "display_fade_by_distance",
            "display_card_art_grid",
            "display_text_shadow",
            "pref_animated_icons",
            "pref_icon_display_mode",
            "pref_icon_display_mode_by_platform",
            "pref_icon1_linger_delay_seconds",
            "pref_video_snap_placement",
            "pref_xmb_game_metadata",
        )
    }

    @Test
    fun `XMB geometry is backed up`() {
        assertCovered(
            "display_xmb_scale",
            "display_bar_top_fraction",
            "display_xmb_layout_adjust",
        )
    }

    @Test
    fun `the one-colour theme cascade is backed up`() {
        assertCovered(
            "theme_accent_override",
            "theme_icon_color",
            "theme_applied_name",
            "theme_layout_spec",
        )
    }

    @Test
    fun `launch and artwork behaviour is backed up`() {
        assertCovered(
            "artwork_import_move_files",
            "pref_dl_manuals",
            "pref_dl_video_snaps",
        )
    }

    @Test
    fun `the Library section's reader choice is backed up`() {
        assertCovered(
            "books_default_reader",

            "book_root_tree_uris",
        )
    }

    @Test
    fun `controller preferences are backed up`() {
        assertCovered(
            "controller_confirm_back_layout",
            "controller_xy_layout",
            "controller_display_type",
            "controller_scroll_speed",
            "controller_left_backs_out",
            "controller_mappings_v1",
        )
    }

    @Test
    fun `the wallpaper and scheme the font colour is measured against are backed up`() {
        assertCovered("display_custom_wallpaper", "display_color_scheme")
    }

    @Test
    fun `keys excluded on purpose stay excluded`() {
        val migrationMarkers = listOf(
            "debug_seeded_v1", "themes_seeded_v1", "library_consolidated_v22", "data_prep_version",
        )

        val danglingStamp = listOf("theme_icons_stamp")

        val sessionState = listOf("achievements_sync_last", "session_blob")

        val derivedCaches = listOf("display_wallpaper_luma", "wallpaper_accent")

        (migrationMarkers + danglingStamp + sessionState + derivedCaches).forEach { key ->
            assertTrue(
                "$key is carried by BackupManager — if that is now intended, move it out of this list",
                key !in covered,
            )
        }
    }
}
