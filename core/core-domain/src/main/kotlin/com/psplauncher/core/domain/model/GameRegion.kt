package com.psplauncher.core.domain.model

enum class GameRegion {
    NTSC_U,
    PAL,
    NTSC_J,
    ;

    companion object {
        fun fromName(name: String?): GameRegion? = name?.let { entries.firstOrNull { e -> e.name == it } }
    }
}
