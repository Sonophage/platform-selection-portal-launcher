package com.psplauncher.core.domain.model

/**
 * The settings tree, in one place, as plain data.
 *
 * Two modules need it and neither may depend on the other: feature-settings draws the section rail
 * down the left of every settings screen from it, and feature-xmb reads it to decide where the
 * crossbar's Settings row lands and which section a screen belongs to. Before this existed the
 * tree lived in XMBViewModel, so the rail would have had to carry a second copy — a list and its
 * mirror, where only the original is ever updated.
 *
 * The crossbar column itself is NOT built from this any more. It is two fixed rows
 * (XMBViewModel.SETTINGS_ROOT_ITEMS): Settings, which opens [SETTINGS_CATALOG].first(), and
 * Android Settings, which is not one of these screens at all.
 *
 * Deliberately Kotlin data and not a resource or a database: the tree is code, it changes when
 * screens are added, and the compiler is the thing that should notice.
 */
enum class SettingsSectionId(
    /** The crossbar row's own id. Distinct from every screen id: the two are routed differently. */
    val id: String,
    val title: String,
    /**
     * What the section covers.
     *
     * Currently unrendered: the rail draws [title] alone. Kept because it is the description a
     * search or a subtitled rail would need, and because writing it once per section is how it
     * stays honest — but nothing displays it today, so treat it as documentation until something
     * does.
     */
    val subtitle: String,
) {
    // FIRST in the enum, because settingsRailRows walks the enum: this is the rail's top group,
    // which is where Settings now lands. A section further down would have opened Settings with
    // the rail's cursor at the bottom of a tree the user had not scrolled to.
    OVERVIEW("settings_section_overview", "Overview", "Library, artwork & build"),
    LIBRARY("settings_section_library", "Library", "Library Manager, collections, artwork & hidden games"),
    EMULATORS("settings_section_emulators", "Emulators", "Launch profiles & RetroArch cores"),
    APPEARANCE("settings_section_appearance", "Appearance", "Theme, wallpaper, layout & boot"),
    INTERFACE("settings_section_interface", "Interface", "Sound, categories, controls & touch"),
    MEDIA("settings_section_media", "Media", "Music, video & photo settings"),
    SYSTEM("settings_section_system", "System", "About, logs, backup, setup & credits"),
}

/**
 * One settings screen: what opens it, what it is called, and which section it belongs to.
 *
 * [subtitle] has no reader today — see [SettingsSectionId.subtitle].
 */
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
    // FIRST on purpose. The crossbar's Settings row opens SETTINGS_CATALOG.first(), so whatever
    // sits here is where Settings lands — and that used to be Library Manager, which dropped the
    // user inside one screen's ROM roots before they had chosen anything.
    SettingsEntry("settings_overview", "Overview", "Library, artwork & build", SettingsSectionId.OVERVIEW),

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

/**
 * Where Settings opens: the list of sections, not one of them.
 *
 * Deliberately NOT in [SETTINGS_CATALOG]. It is not a screen you navigate to from a sibling, it is
 * the page the siblings hang off — and keeping it out is what gives it an empty rail without a
 * special case, since [settingsRailRows] returns nothing for an id the catalog does not know.
 */
const val SETTINGS_ROOT_SCREEN_ID = "settings_root"

/** The screens of one section, in order. */
fun settingsEntriesIn(section: SettingsSectionId): List<SettingsEntry> =
    SETTINGS_CATALOG.filter { it.section == section }

/** The entry a screen id belongs to, or null for a route reached from somewhere else. */
fun settingsEntryFor(screenId: String): SettingsEntry? =
    SETTINGS_CATALOG.firstOrNull { it.id == screenId }

/** The section a screen sits in, or null when it is not one of the catalog's screens. */
fun settingsSectionFor(screenId: String): SettingsSectionId? = settingsEntryFor(screenId)?.section

// ── The section rail ──────────────────────────────────────────────────────────

/**
 * The rail for the screen you are on: the screens of ITS section, and nothing else.
 *
 * It used to list all seven sections with the open one expanded under it — up to thirteen rows,
 * and the open section appeared twice, once as the heading and once as its first screen. The PS5
 * reference this is modelled on puts only the current section's items in that column and uses the
 * section's name as the page title, which is shorter, has no duplicate, and makes the bright row
 * mean one thing.
 *
 * Switching section is [settingsSectionStep], on the shoulders — they were unbound in Settings.
 *
 * Empty for a route outside the catalog. The setup wizard's first-run variant and Library
 * Manager's deep links are reached from elsewhere and have no siblings to move between; a rail
 * there would offer a way out of a screen that is meant to be finished.
 */
fun settingsRailRows(screenId: String?): List<SettingsEntry> =
    screenId?.let(::settingsSectionFor)?.let(::settingsEntriesIn) ?: emptyList()

/**
 * The section [delta] steps from [section], wrapping at both ends.
 *
 * Wrapping, unlike the row cursor inside a screen: the sections are a ring you flick through with
 * the shoulders, and stopping dead at System would make the last section feel like a wall.
 */
fun settingsSectionStep(section: SettingsSectionId, delta: Int): SettingsSectionId {
    val all = SettingsSectionId.entries
    val next = ((section.ordinal + delta) % all.size + all.size) % all.size
    return all[next]
}

/** Where the shoulders land: the first screen of the section [delta] steps away, or null. */
fun settingsSectionStepTarget(screenId: String?, delta: Int): String? {
    val section = screenId?.let(::settingsSectionFor) ?: return null
    return settingsEntriesIn(settingsSectionStep(section, delta)).firstOrNull()?.id
}
