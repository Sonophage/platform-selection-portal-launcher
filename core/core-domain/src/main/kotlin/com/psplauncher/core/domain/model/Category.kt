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
 * (it is user-editable). Library carries position 9 rather than 8 for that reason -- appending it
 * meant an older database gained it without colliding. Last Played is the exception: it is placed
 * here, and moved on established databases by a one-shot in CategoryRepositoryImpl, because it
 * was on the bar for a matter of hours before it was given its home.
 */
val BUILT_IN_CATEGORIES: List<Category> = listOf(
    Category(id = BuiltInCategory.SETTINGS, name = "Settings",  iconKey = "ic_settings", type = CategoryType.BUILT_IN, position = 0),
    Category(id = "photos",                 name = "Photo",     iconKey = "ic_photos",   type = CategoryType.BUILT_IN, position = 1),
    Category(id = "music",                  name = "Music",     iconKey = "ic_music",    type = CategoryType.BUILT_IN, position = 2),
    Category(id = "videos",                 name = "Video",     iconKey = "ic_videos",   type = CategoryType.BUILT_IN, position = 3),
    // Immediately left of Game: "what I was doing" is the thing you reach for first, and the
    // shortest path to it is one step off the column you already live in.
    //
    // isGamingCategory stays FALSE even though every row here is a game. The flag means "games
    // can be assigned to this category": it puts an Add Games row on the column and gives the
    // column a sort cycle (see activeSortModes). This section is derived from last_played_at,
    // nothing can be assigned to it, and its order IS its meaning, so both would be wrong.
    Category(id = BuiltInCategory.RECENTLY_PLAYED, name = "Last Played", iconKey = "ic_recent", type = CategoryType.BUILT_IN, position = 4),
    Category(id = BuiltInCategory.GAMES,    name = "Game",      iconKey = "ic_games",    type = CategoryType.BUILT_IN, position = 5, isGamingCategory = true),
    Category(id = "network",                name = "Network",   iconKey = "ic_network",  type = CategoryType.BUILT_IN, position = 6),
    Category(id = BuiltInCategory.LIBRARY,  name = "Library",   iconKey = "ic_library",  type = CategoryType.BUILT_IN, position = 9),
)
