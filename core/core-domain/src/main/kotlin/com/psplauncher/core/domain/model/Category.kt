package com.psplauncher.core.domain.model

import kotlinx.serialization.Serializable

data class Category(
    val id: String,
    val name: String,
    val iconKey: String,
    val customIconUri: String? = null,
    val accentColor: Long? = null,
    val type: CategoryType,
    val position: Int,
    val isVisible: Boolean = true,
    val filterRules: FilterRules? = null,
    val isGamingCategory: Boolean = false,
)

enum class CategoryType {
    BUILT_IN,
    PLATFORM,
    SMART,
    SHORTCUT_GROUP,
    MANUAL,
}

@Serializable
data class FilterRules(
    val platformIds: List<String> = emptyList(),
    val maxLastPlayedDays: Int? = null,
    val isFavoriteOnly: Boolean = false,
    val genres: List<String> = emptyList(),
)

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

    const val LIBRARY          = "library"

    const val SHELVES          = "shelves"

    val RETIRED_IDS = setOf("social", "achievements", "app_store")
}

val BUILT_IN_CATEGORIES: List<Category> = listOf(

    Category(id = BuiltInCategory.RECENTLY_PLAYED, name = "Last Played", iconKey = "ic_recent", type = CategoryType.BUILT_IN, position = 0),

    Category(id = BuiltInCategory.SHELVES,  name = "Shelves",   iconKey = "ic_favorites", type = CategoryType.BUILT_IN, position = 1),
    Category(id = BuiltInCategory.GAMES,    name = "Game",      iconKey = "ic_games",    type = CategoryType.BUILT_IN, position = 2, isGamingCategory = true),
    Category(id = "music",                  name = "Music",     iconKey = "ic_music",    type = CategoryType.BUILT_IN, position = 3),
    Category(id = "videos",                 name = "Video",     iconKey = "ic_videos",   type = CategoryType.BUILT_IN, position = 4),
    Category(id = "photos",                 name = "Photo",     iconKey = "ic_photos",   type = CategoryType.BUILT_IN, position = 5),
    Category(id = BuiltInCategory.LIBRARY,  name = "Library",   iconKey = "ic_library",  type = CategoryType.BUILT_IN, position = 6),
    Category(id = "network",                name = "Network",   iconKey = "ic_network",  type = CategoryType.BUILT_IN, position = 7),

    Category(id = BuiltInCategory.SETTINGS, name = "Settings",  iconKey = "ic_settings", type = CategoryType.BUILT_IN, position = 11),
)

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
