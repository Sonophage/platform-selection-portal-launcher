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
    const val ACHIEVEMENTS     = "achievements"
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
    val RETIRED_IDS = setOf("social")
}
