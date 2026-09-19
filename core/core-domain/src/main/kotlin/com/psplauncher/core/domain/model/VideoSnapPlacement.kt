package com.psplauncher.core.domain.model

/**
 * Where a game's ICON1 video snap plays once the cursor has rested on it.
 *
 * [ICON] is the PSP's own behaviour: the snap runs inside the 144:80 tile over the static ICON0,
 * and so only applies when the tile is drawn in [IconDisplayMode.ICON0] at all.
 *
 * [BACKGROUND] is the PS3's: the snap runs full-bleed behind the whole crossbar, over the still
 * background art, under the legibility scrim. It has no tile to live in, so it plays whatever
 * [IconDisplayMode] the tile is using.
 *
 * It is a placement, not a switch: whether a snap plays at all is the separate Animated Icons
 * preference, and the battery, thermal and linger gates apply either way. One decoder exists
 * regardless, because the two placements are exclusive.
 */
enum class VideoSnapPlacement(val label: String) {
    ICON("Icon tile"),
    BACKGROUND("Background");

    companion object {
        val DEFAULT = ICON

        fun fromName(name: String?): VideoSnapPlacement? = entries.firstOrNull { it.name == name }
    }
}
