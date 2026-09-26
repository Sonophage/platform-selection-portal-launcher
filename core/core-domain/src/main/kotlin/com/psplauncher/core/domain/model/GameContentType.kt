package com.psplauncher.core.domain.model

enum class GameContentType {
    GAME,
    ANDROID_APP,
    VIDEO_APP,
    MUSIC_APP,
    MEDIA,
    SHORTCUT;

    companion object {
        fun fromName(name: String?): GameContentType =
            entries.firstOrNull { it.name == name } ?: GAME
    }
}
