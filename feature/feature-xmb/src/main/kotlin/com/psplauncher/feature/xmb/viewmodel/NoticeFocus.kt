package com.psplauncher.feature.xmb.viewmodel

/**
 * One thing the notification sheet's cursor can stop on.
 *
 * A notification is named by its KEY, not by its position. The list is live — the app that posted
 * a notification can clear it while the sheet is open — so an index stored as "the third row"
 * quietly becomes a different notification, and the press that was meant to open one opens
 * another. The same lesson the pill row learned: name the thing, never its place in a list.
 */
sealed interface NoticeFocus {
    /** The media row across the top. Present only while something is playing. */
    data object Media : NoticeFocus

    /** One of the device's notifications, by its listener key. */
    data class Notice(val key: String) : NoticeFocus
}

/**
 * How many of the device's notifications the sheet draws, and therefore how many the cursor can
 * reach. It is a glance, not a shade — forty would run off the screen.
 */
const val NOTICE_ROWS = 5
