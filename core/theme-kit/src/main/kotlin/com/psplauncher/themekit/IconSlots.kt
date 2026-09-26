package com.psplauncher.themekit

data class IconSlot(
    val key: String,
    val group: Group,

    val displayName: String,

    val templateSizePx: Int,
) {
    enum class Group { CATEGORY_BAR, ITEMS, STATUS, CONSOLE }
}

object IconSlots {
    private const val CATBAR_TEMPLATE_PX = 256
    private const val ITEM_TEMPLATE_PX = 256
    private const val STATUS_TEMPLATE_PX = 128

    private fun catbar(key: String, name: String) =
        IconSlot(key, IconSlot.Group.CATEGORY_BAR, name, CATBAR_TEMPLATE_PX)

    private fun item(key: String, name: String) =
        IconSlot(key, IconSlot.Group.ITEMS, name, ITEM_TEMPLATE_PX)

    private fun status(key: String, name: String) =
        IconSlot(key, IconSlot.Group.STATUS, name, STATUS_TEMPLATE_PX)

    val ALL: List<IconSlot> = listOf(

        catbar("catbar_games", "Games"),
        catbar("catbar_music", "Music"),
        catbar("catbar_video", "Video"),
        catbar("catbar_photos", "Photos"),
        catbar("catbar_settings", "Settings"),
        catbar("catbar_network", "Network"),
        catbar("catbar_appstore", "App Store"),
        catbar("catbar_favorites", "Favorites"),
        catbar("catbar_library", "Library"),

        item("item_add", "Add / create action"),
        item("item_missing", "Missing games bucket"),

        item("item_memcard_games", "Memory card (Games)"),
        item("item_memcard_music", "Memory card (Music)"),
        item("item_memcard_video", "Memory card (Video)"),
        item("item_memcard_photos", "Memory card (Photos)"),
        item("item_settings", "Settings item (wrench)"),
        item("item_video_folder", "Video folder"),
        item("item_video_library", "Video library"),
        item("item_video_recent", "Recent videos"),
        item("item_video_favorites", "Favorite videos"),
        item("item_video_collections", "Video collections"),
        item("item_video_file", "Video file"),
        item("item_photo_folder", "Photo folder"),
        item("item_photo_file", "Photo file"),
        item("item_photo_albums", "Photo albums"),
        item("item_library_shelves", "Book shelves"),
        item("item_library_reader", "Reader app"),
        item("item_library_folder", "Book folder"),
        item("item_library_book", "Book"),
        item("item_library_series", "Book series"),
        item("item_camera", "Camera"),
        item("item_search", "Search"),
        item("item_music_track", "Music track"),
        item("item_music_artists", "Artists"),
        item("item_music_albums", "Albums"),
        item("item_playlist", "Playlist"),

        status("status_battery_full", "Battery (full)"),
        status("status_battery_high", "Battery (high)"),
        status("status_battery_medium", "Battery (medium)"),
        status("status_battery_low", "Battery (low)"),
        status("status_battery_charging", "Battery (charging)"),
        status("status_bluetooth", "Bluetooth"),
    )

    private val byKey: Map<String, IconSlot> = ALL.associateBy { it.key }

    fun byKey(key: String): IconSlot? = byKey[key]

    fun isValidKey(key: String): Boolean = key in byKey
}
