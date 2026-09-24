package com.psplauncher.core.domain.model

import kotlinx.serialization.Serializable

data class Category(
    val id: String,
    val name: String,
    val iconKey: String,                // built-in icon key or "custom"
    val customIconUri: String? = null,
    val accentColor: Long? = null,      // per-category wave color override
    val type: CategoryType,
    val position: Int,
    val isVisible: Boolean = true,
    val filterRules: FilterRules? = null, // only for SMART type
    val isGamingCategory: Boolean = false, // true for games/collections, false for apps
)

enum class CategoryType {
    BUILT_IN,       // Favorites, Recently Played, Games, Android, App Drawer, Settings
    PLATFORM,       // Pinned platform — mirrors a Platform entry
    SMART,          // Auto-populates by filter rules
    SHORTCUT_GROUP, // Groups specific app shortcuts
    MANUAL,         // User hand-picks items
}

@Serializable
data class FilterRules(
    val platformIds: List<String> = emptyList(),
    val maxLastPlayedDays: Int? = null,
    val isFavoriteOnly: Boolean = false,
    val genres: List<String> = emptyList(),
)

// Built-in category IDs — never change these
object BuiltInCategory {
    const val FAVORITES        = "favorites"
    const val RECENTLY_PLAYED  = "recently_played"
    const val GAMES            = "games"
    const val MUSIC            = "music"
    const val VIDEO            = "videos"
    const val PHOTO            = "photos"
    const val ANDROID          = "android"
    const val APP_DRAWER       = "app_drawer"
    const val SETTINGS         = "settings"
    /**
     * The Library section (books). Deliberately not "books": a user-made custom category of
     * that name already exists on established databases, and a built-in sharing its id would
     * collide with it.
     */
    const val LIBRARY          = "library"

    /**
     * Built-in categories this build no longer has. A row for one of these can still reach a live
     * database two ways: an install seeded by an older build, and a restored backup, which upserts
     * whatever categories the archive carried. Either way the column would draw with no icon and
     * do nothing when selected, so `CategoryRepositoryImpl.pruneRetiredCategories()` sweeps them —
     * the same job `UiMediaStore.pruneOrphans()` does for retired media slots. As with those keys,
     * a retired id is deliberately NOT reused.
     */
    //
    // "app_store" is the odd one here: it is not a leftover from an older build but a column the
    // owner asked to remove. It held one row — Play Store — so a whole column bought one app that
    // the App Drawer already lists. Retiring it rather than hiding it means the sweep also takes
    // the app assignments that hung off it, on his install and on any restored backup.
    val RETIRED_IDS = setOf("social", "achievements", "app_store")
}

/**
 * The built-in categories, defined ONCE.
 *
 * There were two copies: `CategoryRepositoryImpl.BUILT_IN_CATEGORIES`, which called itself the
 * single source of truth and is used for first-launch seeding and per-launch reconciliation, and
 * `XMBViewModel.FALLBACK_CATEGORIES`, a byte-for-byte copy of the first seven rows used as the
 * crossbar's cold-start list. They drifted: Library was added to one and not the other.
 *
 * That was not only a cold-start cosmetic difference. `canonicalXmbCategories` derives its set of
 * built-in ids from the crossbar's copy, so a category missing from it fell through to the
 * custom-category path and lost the canonical icon every built-in is guaranteed; and
 * `observeCategoryBar` falls back to that copy on an empty read, where the missing section
 * vanished from the bar entirely.
 *
 * The sting is that `canonicalXmbCategories` exists BECAUSE the bar and the repository drifted
 * apart once before. The merge rule was guarded. The two lists it merged were not. There is one
 * list now, so there is nothing left to guard.
 *
 * Order here is the bar's order, and it applies to a FRESH install only: an established database
 * keeps the positions it already holds, because reconciliation deliberately leaves position alone
 * (it is user-editable).
 *
 * THESE NUMBERS ARE READ OFF A REAL INSTALL. They were a first guess at a sensible order, and the
 * owner then arranged his own bar and asked for that arrangement to be the default — so the
 * positions below are the ones his database actually holds, copied across. A default that
 * disagrees with the only arrangement anyone has lived with is a default nobody chose.
 *
 * Last Played keeps its one-shot move in CategoryRepositoryImpl for databases seeded before it
 * had a home.
 */
val BUILT_IN_CATEGORIES: List<Category> = listOf(
    // Last Played first. It has no caticon — the page REPLACES the whole screen — so this
    // position is what "one step left of Emulation" means rather than a slot on the bar.
    Category(id = BuiltInCategory.RECENTLY_PLAYED, name = "Last Played", iconKey = "ic_recent", type = CategoryType.BUILT_IN, position = 0),
    Category(id = BuiltInCategory.GAMES,    name = "Game",      iconKey = "ic_games",    type = CategoryType.BUILT_IN, position = 1, isGamingCategory = true),
    Category(id = "music",                  name = "Music",     iconKey = "ic_music",    type = CategoryType.BUILT_IN, position = 2),
    Category(id = "videos",                 name = "Video",     iconKey = "ic_videos",   type = CategoryType.BUILT_IN, position = 3),
    Category(id = "photos",                 name = "Photo",     iconKey = "ic_photos",   type = CategoryType.BUILT_IN, position = 4),
    Category(id = BuiltInCategory.LIBRARY,  name = "Library",   iconKey = "ic_library",  type = CategoryType.BUILT_IN, position = 5),
    Category(id = "network",                name = "Network",   iconKey = "ic_network",  type = CategoryType.BUILT_IN, position = 6),
    // Last, with room left before it: a custom category made by the user lands between Network
    // and Settings rather than past the end of the bar.
    Category(id = BuiltInCategory.SETTINGS, name = "Settings",  iconKey = "ic_settings", type = CategoryType.BUILT_IN, position = 11),
)

/**
 * Where a built-in lands when it is seeded into a database that already exists.
 *
 * A row the database already has keeps its own position untouched — that is the user's
 * arrangement, and reconciliation has never been allowed to move it. A row it has never seen is
 * APPENDED past everything, whatever number the constant gives it.
 *
 * That last part is the whole rule. The constant's positions are the fresh-install ORDER; on an
 * established database that order means nothing, and the number for a column the user has never
 * seen is as likely as not to be one of theirs. Two rows sharing a position is a bar whose order
 * depends on which row the query returns first.
 *
 * It replaces the convention it used to take instead: that every new built-in must be given a
 * position past every old one. That worked, and it meant the default order could never be
 * rearranged — every column added after the first release had to go on the end whatever the bar
 * should read like.
 *
 * [highestExisting] is null for an empty database, which is a FRESH install: the defaults are
 * used as written, because there is nothing to collide with and the order is the point.
 */
fun seededPositions(
    defaults: List<Category>,
    existingIds: Set<String>,
    highestExisting: Int?,
): List<Category> {
    if (highestExisting == null) return defaults
    var next = highestExisting + 1
    return defaults.map { category ->
        if (category.id in existingIds) category else category.copy(position = next++)
    }
}
