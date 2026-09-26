package com.psplauncher.core.domain.model

enum class IconDisplayMode(val label: String) {
    ICON0("Custom Icon"),
    BOX_ART("Box Art"),
    PHYSICAL_MEDIA("Physical Media"),
    BOX_3D("3D Box Art");

    companion object {
        val DEFAULT = ICON0

        fun fromName(name: String?): IconDisplayMode? = entries.firstOrNull { it.name == name }
    }
}
