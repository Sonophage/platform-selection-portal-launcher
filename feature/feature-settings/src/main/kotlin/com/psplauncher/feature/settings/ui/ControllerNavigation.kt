package com.psplauncher.feature.settings.ui

import com.psplauncher.core.navigation.NavigationEngine
import com.psplauncher.core.navigation.NavigationNode
import com.psplauncher.core.navigation.NavigationTouchAction
import kotlin.math.abs

data class ControllerNavItem(
    val key: String,
    val focusable: Boolean = true,
    val selectable: Boolean = true,
    val enabled: Boolean = true,
    val onSelect: (() -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,

    val trailingActions: List<ControllerNavItem> = emptyList(),
)

internal fun ControllerNavItem.toNavigationNode(): NavigationNode = NavigationNode(
    key = key,
    focusable = focusable,
    selectable = selectable,
    enabled = enabled,
    onSelect = onSelect,
    onLongPress = onLongPress,

    children = trailingActions.map { it.toNavigationNode() },
)

class ControllerNavigationState(
    private val engine: NavigationEngine = NavigationEngine("settings"),
) {
    var focusedKey: String? = null
        private set

    val acceptsInput: Boolean get() = engine.acceptsInput
    val cursorVisible: Boolean get() = engine.cursorVisible

    fun markControllerInput() = engine.markControllerInput()
    fun markTouchInput() = engine.markTouchInput()

    fun focusNearestTo(y: Float): String? {
        val target = engine.currentGeometry()
            .filterKeys { key -> key in engine.focusableKeys() }
            .minByOrNull { (_, nodeY) -> abs(nodeY - y) }
            ?.key
        if (target != null) engine.setFocused(target)
        focusedKey = engine.focusedKey
        return focusedKey
    }

    fun touch(key: String, longPress: Boolean = false): Boolean =
        engine.dispatchTouch(key, if (longPress) NavigationTouchAction.LONG_PRESS else NavigationTouchAction.TAP)

    fun updateItems(
        newItems: List<ControllerNavItem>,
        geometry: Map<String, Float> = emptyMap(),
    ) {
        engine.replaceNodes(newItems.map { it.toNavigationNode() }, geometry)
        focusedKey = engine.focusedKey
    }

    fun move(delta: Int): String? {
        focusedKey = engine.moveVerticalActive(delta)
        return focusedKey
    }

    fun moveHorizontal(delta: Int): String? {
        focusedKey = engine.moveHorizontalActive(delta)
        return focusedKey
    }

    fun focusFirst(): String? {
        engine.focusFirst()
        focusedKey = engine.focusedKey
        return focusedKey
    }

    fun setFocused(key: String?) {
        engine.setFocused(key)
        focusedKey = engine.focusedKey
    }

    fun select(): Boolean = engine.confirmDirect()
}
