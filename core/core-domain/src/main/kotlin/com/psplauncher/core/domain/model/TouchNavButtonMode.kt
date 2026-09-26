package com.psplauncher.core.domain.model

enum class TouchNavButtonMode {
    AUTO,
    ALWAYS_SHOW,
    ALWAYS_HIDE;

    companion object {
        fun fromName(value: String?): TouchNavButtonMode =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}
