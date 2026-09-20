package com.psplauncher.core.domain.model

/**
 * The settings tree, in one place, as plain data.
 *
 * Two modules need it and neither may depend on the other: feature-xmb builds the crossbar's
 * Settings column from it, and feature-settings draws the section rail down the left of every
 * settings screen from it. Before this existed the tree lived in XMBViewModel, so the rail would
 * have had to carry a second copy — a list and its mirror, where only the original is ever
 * updated.
 *
 * Deliberately Kotlin data and not a resource or a database: the tree is code, it changes when
 * screens are added, and the compiler is the thing that should notice.
 */
enum class SettingsSectionId(
    /** The crossbar row's own id. Distinct from every screen id: the two are routed differently. */
    val id: String,
    val title: String,
    val subtitle: String,
) {
    LIBRARY("settings_section_library", "Library", "Library Manager, collections, artwork & hidden games"),
    EMULATORS("settings_section_emulators", "Emulators", "Launch profiles & RetroArch cores"),
    APPEARANCE("settings_section_appearance", "Appearance", "Theme, wallpaper, layout & boot"),
    INTERFACE("settings_section_interface", "Interface", "Sound, categories, controls & touch"),
    MEDIA("settings_section_media", "Media", "Music, video & photo settings"),
    SYSTEM("settings_section_system", "System", "About, logs, backup, setup & credits"),
}

/** One settings screen: what opens it, what it is called, and which section it belongs to. */
data class SettingsEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val section: SettingsSectionId,
)

/**
 * Every settings screen reachable from the crossbar, in the order both the column and the rail
 * show them.
 *
 * Not every route in SETTINGS_SCREEN_ROUTES appears here, and that is deliberate: routes like
 * the setup wizard's first-run variant or Library Manager's deep links are reached from
 * elsewhere and are not places you navigate to from a sibling list.
 */
val SETTINGS_CATALOG: List<SettingsEntry> = listOf(
    SettingsEntry("settings_library", "Library Manager", "ROM sources & scanning", SettingsSectionId.LIBRARY),
    SettingsEntry("settings_windows_games", "Windows Games", "PC games, launchers & imports", SettingsSectionId.LIBRARY),
    SettingsEntry("settings_collections", "Collections", "Create & manage game collections", SettingsSectionId.LIBRARY),
    SettingsEntry("settings_artwork", "Artwork", "Your art, scraping & cache", SettingsSectionId.LIBRARY),
    SettingsEntry("settings_artwork_sources", "Scraping Sources", "Source priority & service accounts", SettingsSectionId.LIBRARY),
    SettingsEntry("settings_app_visibility", "Hidden Items", "Review apps & games you've hidden", SettingsSectionId.LIBRARY),

    SettingsEntry("settings_emulators_installed", "Installed", "Detected emulator profiles", SettingsSectionId.EMULATORS),
    SettingsEntry("settings_emulators_custom", "Custom Emulators", "Custom profiles & Add Custom Emulator", SettingsSectionId.EMULATORS),
    SettingsEntry("settings_emulators_retroarch", "RetroArch", "Core detection & linking", SettingsSectionId.EMULATORS),
    SettingsEntry("settings_emulators_assign", "Per-System Defaults", "Default emulator & core per console, and per-game overrides", SettingsSectionId.EMULATORS),

    SettingsEntry("settings_themes", "Theme", "Colour scheme, accent & theme packs", SettingsSectionId.APPEARANCE),
    SettingsEntry("settings_appearance", "Wallpaper & Text", "Wallpaper, wave, motion & legibility", SettingsSectionId.APPEARANCE),
    SettingsEntry("settings_layout", "Layout", "XMB layout, custom icons & orientation", SettingsSectionId.APPEARANCE),
    SettingsEntry("settings_boot", "Boot", "Boot sequence, boot video & GameBoot", SettingsSectionId.APPEARANCE),

    SettingsEntry("settings_audio", "Sound", "Menu & boot sounds", SettingsSectionId.INTERFACE),
    SettingsEntry("settings_categories", "Categories", "Manage XMB categories", SettingsSectionId.INTERFACE),
    SettingsEntry("settings_controller", "Controller", "Button mapping", SettingsSectionId.INTERFACE),
    SettingsEntry("settings_touch", "Touch", "On-screen button, swipe & hints", SettingsSectionId.INTERFACE),

    SettingsEntry("settings_music", "Music", "Music folders & default player", SettingsSectionId.MEDIA),
    SettingsEntry("settings_video", "Video", "Video libraries, scanning & playback", SettingsSectionId.MEDIA),
    SettingsEntry("settings_photo", "Photo", "Photo libraries & scanning", SettingsSectionId.MEDIA),
    SettingsEntry("settings_books", "Books", "Book folders & reader", SettingsSectionId.MEDIA),

    SettingsEntry("settings_about", "About", "PSPLauncher", SettingsSectionId.SYSTEM),
    SettingsEntry("settings_logs", "Logs", "Debug & error log viewer", SettingsSectionId.SYSTEM),
    SettingsEntry("settings_backup", "Backup & Restore", "Export & import", SettingsSectionId.SYSTEM),
    SettingsEntry("settings_performance", "Performance", "Thermal, battery saver & direct launch", SettingsSectionId.SYSTEM),
    SettingsEntry("settings_initial_setup", "Setup Wizard", "Guided folder & account setup", SettingsSectionId.SYSTEM),
    SettingsEntry("settings_credits", "Credits", "Artwork & attributions", SettingsSectionId.SYSTEM),
)

/** The screens of one section, in order. */
fun settingsEntriesIn(section: SettingsSectionId): List<SettingsEntry> =
    SETTINGS_CATALOG.filter { it.section == section }

/** The entry a screen id belongs to, or null for a route reached from somewhere else. */
fun settingsEntryFor(screenId: String): SettingsEntry? =
    SETTINGS_CATALOG.firstOrNull { it.id == screenId }

/** The section a screen sits in, or null when it is not one of the catalog's screens. */
fun settingsSectionFor(screenId: String): SettingsSectionId? = settingsEntryFor(screenId)?.section
