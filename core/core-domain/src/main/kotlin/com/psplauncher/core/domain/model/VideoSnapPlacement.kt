package com.psplauncher.core.domain.model

enum class VideoSnapPlacement(val label: String) {
    ICON("Icon tile"),
    BACKGROUND("Background");

    companion object {
        val DEFAULT = ICON

        fun fromName(name: String?): VideoSnapPlacement? = entries.firstOrNull { it.name == name }
    }
}
