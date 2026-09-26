package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.GamepadAction

data class XmbPrompt(val action: GamepadAction, val verb: String, val target: String? = null)

data class XmbPrompts(
    val primary: XmbPrompt?,
    val back: XmbPrompt,
    val right: List<XmbPrompt>,
)

internal fun primaryVerbFor(item: XMBItem?, directLaunch: Boolean): String? = when {
    item == null || item.type == XMBItemType.EMPTY -> null
    item.gameId != null -> if (directLaunch) "Play" else "Details"
    item.type == XMBItemType.MUSIC_TRACK -> "Play"
    item.type == XMBItemType.VIDEO_FILE -> "Play"
    item.type == XMBItemType.LIBRARY_BOOK -> "Read"
    item.type == XMBItemType.PHOTO_FILE -> "View"
    item.type == XMBItemType.ADD_ACTION -> "Add"
    item.type == XMBItemType.SEARCH -> "Search"
    item.packageName != null -> "Launch"
    else -> "Open"
}

fun promptsFor(state: XMBUiState): XmbPrompts {
    val focused = state.currentItems.getOrNull(state.selectedItemIndex)

    if (state.notificationsOpen) {
        val focus = state.focusedNotice
        val notice = (focus as? NoticeFocus.Notice)
            ?.let { row -> state.androidNotices.firstOrNull { it.key == row.key } }
        return XmbPrompts(
            primary = when {
                focus == NoticeFocus.Media -> XmbPrompt(
                    GamepadAction.SELECT,
                    if (state.musicPlayback.track != null) {
                        if (state.musicPlayback.isPlaying) "Pause" else "Play"
                    } else "Resume",
                    state.musicPlayback.track?.let { it.title ?: it.displayName } ?: state.resumeGame?.displayTitle,
                )
                notice?.canOpen == true -> XmbPrompt(GamepadAction.SELECT, "Open", notice.appLabel)
                else -> null
            },
            back = XmbPrompt(GamepadAction.BACK, "Close"),
            right = buildList {
                if (notice?.canDismiss == true) add(XmbPrompt(GamepadAction.OPEN_CONTEXT_MENU, "Clear"))
            },
        )
    }

    state.activeContextMenu?.let { menu ->
        val row = menu.selectedIndex?.let { state.menuRows().getOrNull(it) }
        val primaryVerb = primaryVerbFor(focused, state.directLaunch)
        return XmbPrompts(
            primary = when {
                row != null -> XmbPrompt(GamepadAction.SELECT, "Select", row.label)

                menu.primaryId != null && primaryVerb != null ->
                    XmbPrompt(GamepadAction.SELECT, primaryVerb, focused?.title)
                else -> null
            },
            back = XmbPrompt(GamepadAction.BACK, "Close"),
            right = emptyList(),
        )
    }

    val right = buildList {
        when {
            state.canFilterRecents -> add(XmbPrompt(GamepadAction.CHANGE_SORT, "Filter"))
            state.canSortCurrentList -> add(XmbPrompt(GamepadAction.CHANGE_SORT, "Sort"))
        }
        if (state.focusedItemHasContextMenu) add(XmbPrompt(GamepadAction.OPEN_CONTEXT_MENU, "Options"))
        if (!state.isInSubItem) add(XmbPrompt(GamepadAction.OPEN_SEARCH, "Search"))
    }

    return XmbPrompts(
        primary = primaryVerbFor(focused, state.directLaunch)?.let {
            XmbPrompt(GamepadAction.SELECT, it, focused?.title)
        },

        back = XmbPrompt(GamepadAction.BACK, if (state.isInSubItem) "Back" else "Apps"),
        right = right,
    )
}
