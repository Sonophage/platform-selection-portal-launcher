package com.psplauncher.core.domain.model

enum class HideLocationType { GLOBAL, CATEGORY, COLLECTION, ANDROID_PLATFORM, PLATFORM, FAVORITES, ALL_GAMES, RECENTS }

data class HiddenPlacement(
    val itemKey: String,
    val itemLabel: String,
    val locationType: HideLocationType,
    val locationId: String,
    val locationLabel: String,
    val createdAt: Long,
) {
    companion object {
        fun appKey(packageName: String) = "app:$packageName"
        fun gameKey(gameId: Long) = "game:$gameId"
    }
}
