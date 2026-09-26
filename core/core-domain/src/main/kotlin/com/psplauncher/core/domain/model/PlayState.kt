package com.psplauncher.core.domain.model

enum class PlayState(

    val label: String,

    val mark: String,
) {
    PLAYING("Playing", "▶"),
    COMPLETED("Completed", "✓"),
    BACKLOG("Backlog", "＋");

    companion object {
        fun fromName(name: String?): PlayState? = entries.firstOrNull { it.name == name }
    }
}
