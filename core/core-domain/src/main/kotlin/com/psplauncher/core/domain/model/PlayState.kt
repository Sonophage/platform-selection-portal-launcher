package com.psplauncher.core.domain.model

/**
 * Where a game stands with you, as you say it stands.
 *
 * **Marked by hand, exactly like a favourite, and never inferred.** The launcher knows play time
 * and last-played and could guess from either, and every guess is wrong somewhere obvious: forty
 * hours in an endless roguelike is not "completed", and a game finished in one sitting the week
 * it arrived looks identical to one abandoned in the menu. A shelf that quietly decides you
 * finished something you did not is worse than a shelf that only knows what you told it.
 *
 * Null is the fourth state and the default: unmarked. It is not BACKLOG — a library of 152 games
 * you have never sorted is not a backlog of 152 games, and saying so would make the badge
 * meaningless on the day it shipped.
 *
 * Stored by [name], the way [IconDisplayMode] is, so the column stays readable in a sqlite shell
 * and an unknown value reads as null rather than crashing.
 */
enum class PlayState(
    /** What the menu row calls it. */
    val label: String,
    /** The one character the badge draws, where a word would not fit. */
    val mark: String,
) {
    PLAYING("Playing", "▶"),
    COMPLETED("Completed", "✓"),
    BACKLOG("Backlog", "＋");

    companion object {
        fun fromName(name: String?): PlayState? = entries.firstOrNull { it.name == name }
    }
}
