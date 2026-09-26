package com.psplauncher.core.domain.model

enum class IconLegibilityStyle(val label: String) {
    NONE("None"),
    OFFSET_SHADOW("Offset Shadow"),
    CONTOUR_DARK("Contour (Dark)"),
    CONTOUR_LIGHT("Contour (Light)"),
    CONTOUR_AUTO("Contour (Auto)");

    companion object {
        val DEFAULT = NONE

        fun fromName(value: String?): IconLegibilityStyle =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}
