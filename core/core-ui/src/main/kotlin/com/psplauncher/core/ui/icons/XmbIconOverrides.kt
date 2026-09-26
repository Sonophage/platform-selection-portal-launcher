package com.psplauncher.core.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

val LocalXmbIconOverrides = staticCompositionLocalOf<Map<String, CustomIcon>> { emptyMap() }

@Composable
fun ThemedGlyph(
    slotKey: String,
    defaultVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val icon = LocalCustomIcons.current[slotKey] ?: LocalXmbIconOverrides.current[slotKey]
    if (icon != null) {
        CustomIconSurface(icon, contentDescription, modifier)
        return
    }
    VectorGlyphSurface(vector = defaultVector, contentDescription = contentDescription, tint = tint, modifier = modifier)
}

fun catbarSlotKeyFor(iconKey: String): String? = CATBAR_SLOT_KEYS[categoryIconFor(iconKey).key]

fun catbarIconKeyFor(slotKey: String): String? = CATBAR_ICON_KEYS[slotKey]

private val CATBAR_SLOT_KEYS: Map<String, String> = mapOf(
    "ic_settings" to "catbar_settings",
    "ic_photos" to "catbar_photos",
    "ic_music" to "catbar_music",
    "ic_videos" to "catbar_video",
    "ic_games" to "catbar_games",
    "ic_network" to "catbar_network",
    "ic_appstore" to "catbar_appstore",
    "ic_favorites" to "catbar_favorites",
    "ic_library" to "catbar_library",
    "ic_recent" to "catbar_recent",
)

private val CATBAR_ICON_KEYS: Map<String, String> =
    CATBAR_SLOT_KEYS.entries.associate { (iconKey, slotKey) -> slotKey to iconKey }
