package com.psplauncher.core.domain.model

enum class SettingsSectionId(

    val id: String,
    val title: String,

    val subtitle: String,
) {
    OVERVIEW("settings_section_overview", "Overview", "Library, artwork & build"),
    LIBRARY("settings_section_library", "Library", "Library Manager, collections, artwork & hidden games"),
    EMULATORS("settings_section_emulators", "Emulators", "Launch profiles & RetroArch cores"),
    APPEARANCE("settings_section_appearance", "Appearance", "Theme, wallpaper, layout & boot"),
    INTERFACE("settings_section_interface", "Interface", "Sound, categories, controls & touch"),
    MEDIA("settings_section_media", "Media", "Music, video & photo settings"),
    SYSTEM("settings_section_system", "System", "About, logs, backup, setup & credits"),
}

data class SettingsEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val section: SettingsSectionId,
)

val SETTINGS_CATALOG: List<SettingsEntry> = listOf(

    SettingsEntry("settings_overview", "Overview", "Library, artwork & build", SettingsSectionId.OVERVIEW),

    SettingsEntry("settings_library", "Library Manager", "ROM sources & scanning", SettingsSectionId.LIBRARY),
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
    SettingsEntry("settings_categories", "Categories", "XMB categories & the collections inside them", SettingsSectionId.INTERFACE),
    SettingsEntry("settings_controller", "Controller", "Button mapping", SettingsSectionId.INTERFACE),
    SettingsEntry("settings_touch", "Touch", "On-screen button, swipe & hints", SettingsSectionId.INTERFACE),
    SettingsEntry("settings_performance", "Performance", "Thermal, battery saver & direct launch", SettingsSectionId.INTERFACE),

    SettingsEntry("settings_music", "Music", "Music folders & default player", SettingsSectionId.MEDIA),
    SettingsEntry("settings_video", "Video", "Video libraries, scanning & playback", SettingsSectionId.MEDIA),
    SettingsEntry("settings_photo", "Photo", "Photo libraries & scanning", SettingsSectionId.MEDIA),
    SettingsEntry("settings_books", "Books", "Book folders & reader", SettingsSectionId.MEDIA),

    SettingsEntry("settings_initial_setup", "Setup Wizard", "Guided folder & account setup", SettingsSectionId.SYSTEM),
    SettingsEntry("settings_about", "About", "PSPLauncher", SettingsSectionId.SYSTEM),
    SettingsEntry("settings_logs", "Logs", "Debug & error log viewer", SettingsSectionId.SYSTEM),
    SettingsEntry("settings_backup", "Backup & Restore", "Export & import", SettingsSectionId.SYSTEM),
    SettingsEntry("settings_credits", "Credits", "Artwork & attributions", SettingsSectionId.SYSTEM),
)

const val SETTINGS_ROOT_SCREEN_ID = "settings_root"

fun settingsEntriesIn(section: SettingsSectionId): List<SettingsEntry> =
    SETTINGS_CATALOG.filter { it.section == section }

fun settingsEntryFor(screenId: String): SettingsEntry? =
    SETTINGS_CATALOG.firstOrNull { it.id == screenId }

fun settingsSectionFor(screenId: String): SettingsSectionId? = settingsEntryFor(screenId)?.section

fun settingsRailRows(screenId: String?): List<SettingsEntry> =
    screenId?.let(::settingsSectionFor)?.let(::settingsEntriesIn) ?: emptyList()

fun settingsSectionStep(section: SettingsSectionId, delta: Int): SettingsSectionId {
    val all = SettingsSectionId.entries
    val next = ((section.ordinal + delta) % all.size + all.size) % all.size
    return all[next]
}

fun settingsSectionStepTarget(screenId: String?, delta: Int): String? {
    val section = screenId?.let(::settingsSectionFor) ?: return null
    return settingsEntriesIn(settingsSectionStep(section, delta)).firstOrNull()?.id
}
