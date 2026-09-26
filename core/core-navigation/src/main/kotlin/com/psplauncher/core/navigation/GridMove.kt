package com.psplauncher.core.navigation

fun gridMove(
    current: Int,
    direction: NavigationDirection,
    columns: Int,
    size: Int,
): Int? {
    if (size <= 0 || columns <= 0) return null
    if (current !in 0 until size) return null
    return when (direction) {
        NavigationDirection.LEFT -> if (current % columns > 0) current - 1 else null
        NavigationDirection.RIGHT ->
            if (current % columns < columns - 1 && current + 1 < size) current + 1 else null
        NavigationDirection.UP -> if (current - columns >= 0) current - columns else null
        NavigationDirection.DOWN -> if (current + columns < size) current + columns else null
    }
}
