package com.psplauncher.core.domain.model

/**
 * Where an item can be hidden from. GLOBAL keeps the legacy "hide everywhere" behaviour.
 * PLATFORM hides a game from one Memory Card ([HiddenPlacement.locationId] = the platform id);
 * ANDROID_PLATFORM predates it and remains the location for the Android card's hides.
 * ALL_GAMES hides a game from the aggregated All Games card only — it stays on its own
 * Memory Card, in collections, and in Favorites.
 *
 * RECENTS is the home shelf, and it exists because an app's recency is not ours to clear. A game
 * has a last_played_at this app wrote and can therefore un-write; an app's comes from Android's
 * UsageStatsManager, which has no such door. So "Remove from Recent" on an app is a hide record
 * against this location rather than a deletion, and the app returns to the shelf if the user
 * un-hides it in Settings ▸ Hidden Items. [HiddenPlacement.locationId] is empty for it, as it is
 * for ANDROID_PLATFORM and FAVORITES — there is only one home shelf.
 *
 * Persisted by NAME (see HiddenPlacementEntity), and read back through
 * `runCatching { valueOf(..) }.getOrDefault(GLOBAL)`. Adding a constant is therefore safe for
 * existing rows; note the reverse is not, since an older build reading a RECENTS row would widen
 * it to GLOBAL and hide the app everywhere.
 */
enum class HideLocationType { GLOBAL, CATEGORY, COLLECTION, ANDROID_PLATFORM, PLATFORM, FAVORITES, ALL_GAMES, RECENTS }

/**
 * A single "this item is hidden from this location" record. An item (an Android app or a
 * game/shortcut) can have several placements — e.g. hidden from one category and one collection but
 * still visible elsewhere. Labels are cached so the Hidden Items manager renders without extra
 * lookups. [locationId] is the category/collection id; it's empty for ANDROID_PLATFORM / FAVORITES.
 */
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
