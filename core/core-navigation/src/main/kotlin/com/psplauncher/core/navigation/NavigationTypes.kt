package com.psplauncher.core.navigation

enum class NavigationDirection { UP, DOWN, LEFT, RIGHT }

enum class NavigationTouchAction { TAP, LONG_PRESS }

data class NavigationNode(
    val key: String,
    val focusable: Boolean = true,
    val selectable: Boolean = true,
    val enabled: Boolean = true,

    val onSelect: (() -> Unit)? = null,

    val onLongPress: (() -> Unit)? = null,

    val children: List<NavigationNode> = emptyList(),

    val onEditStart: (() -> EditModeHandler?)? = null,
)

fun interface NavigationLogger {
    fun warn(message: String)

    companion object {
        val NONE: NavigationLogger = NavigationLogger { }
    }
}

interface EditModeHandler {
    fun onDirection(direction: NavigationDirection): Boolean

    fun onConfirm(): Boolean

    fun onExit()
}
