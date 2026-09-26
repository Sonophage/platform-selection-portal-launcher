package com.psplauncher.core.navigation

class NavigationEngine(
    contextId: String = "root",
    private val logger: NavigationLogger = NavigationLogger.NONE,
) {
    private val contexts = ArrayDeque<NavigationContext>().apply {
        add(NavigationContext(contextId))
    }

    private var ready = false
    private var inputLocked = false

    val focusedKey: String?
        get() = active.focusedKey

    val activeContextId: String
        get() = active.id

    val isModalActive: Boolean
        get() = contexts.size > 1

    val isEditing: Boolean
        get() = active.editHandler != null

    val acceptsInput: Boolean
        get() = ready && !inputLocked

    var cursorVisible: Boolean = true
        private set

    fun markTouchInput() {
        cursorVisible = false
    }

    fun markControllerInput() {
        cursorVisible = true
    }

    fun dispatchTouch(key: String, action: NavigationTouchAction): Boolean {
        if (!ready || inputLocked) return false
        val node = active.findNode(key) ?: return false
        markTouchInput()
        active.setFocused(key)
        return when (action) {
            NavigationTouchAction.TAP -> active.confirm()
            NavigationTouchAction.LONG_PRESS -> node.onLongPress?.let { it(); true } ?: false
        }
    }

    private val active: NavigationContext
        get() = contexts.last()

    fun markReady() {
        ready = true
    }

    fun recoverReadiness() {
        if (ready) return
        logger.warn(
            "NavigationEngine: screen '${active.id}' never reported readiness — " +
                "forcing recovery so navigation is not permanently broken."
        )
        markReady()
    }

    fun replaceNodes(nodes: List<NavigationNode>, geometry: Map<String, Float> = emptyMap()) {
        active.updateNodes(nodes, geometry)
    }

    fun replaceNodesWithGeometry(
        nodes: List<NavigationNode>,
        geometry: Map<String, Float>,
        previousGeometry: Map<String, Float> = emptyMap(),
    ) {
        active.updateNodes(nodes, geometry, previousGeometry)
    }

    fun focusFirst() {
        active.focusFirst()
    }

    fun setFocused(key: String?) {
        active.setFocused(key)
    }

    fun reportNodeGeometry(key: String, y: Float) {
        active.reportGeometry(key, y)
    }

    fun focusableKeys(): Set<String> = active.nodes.filter { it.focusable && it.enabled }.mapTo(mutableSetOf()) { it.key }

    fun currentGeometry(): Map<String, Float> = active.allGeometry()

    fun pushModal(contextId: String) {
        contexts.addLast(NavigationContext(contextId, modal = true))
    }

    fun popContext(): String? {
        if (contexts.size <= 1) return active.focusedKey
        val closing = contexts.removeLast()
        closing.clearEditHandler()
        return active.focusedKey
    }

    fun dispatch(command: NavigationCommand): String? {
        markControllerInput()

        if (!ready) return null

        if (inputLocked) return null

        val context = active
        val handler = context.editHandler

        if (handler != null) {
            when (command) {
                is NavigationCommand.Direction -> {
                    if (handler.onDirection(command.direction)) return null
                    return dispatchDirection(context, command.direction)
                }
                NavigationCommand.Confirm -> {
                    return if (handler.onConfirm()) null else context.focusedKey
                }
                is NavigationCommand.Back -> {
                    context.clearEditHandler()
                    return null
                }
            }
        }

        return when (command) {
            is NavigationCommand.Direction -> dispatchDirection(context, command.direction)
            NavigationCommand.Confirm -> {
                val key = context.focusedKey
                if (key != null) {
                    val editHandler = context.editHandlerFor(key)
                    if (editHandler != null) return null
                }
                if (context.confirm()) key else null
            }
            is NavigationCommand.Back -> {
                context.clearEditHandler()
                backHandler?.invoke()
                null
            }
        }
    }

    private fun dispatchDirection(context: NavigationContext, direction: NavigationDirection): String? {
        return when (direction) {
            NavigationDirection.UP -> context.moveVertical(-1)
            NavigationDirection.DOWN -> context.moveVertical(1)
            NavigationDirection.LEFT -> context.moveHorizontal(-1)
            NavigationDirection.RIGHT -> context.moveHorizontal(1)
        }
    }

    var backHandler: (() -> Unit)? = null

    fun beginRecoveryLock() {
        inputLocked = true
    }

    fun endRecoveryLock() {
        inputLocked = false
    }

    internal fun activeContextForTest(): NavigationContext = active

    fun confirmDirect(): Boolean = active.confirm()

    fun moveActive(direction: NavigationDirection): String? =
        dispatchDirection(active, direction)

    fun moveVerticalActive(delta: Int): String? = active.moveVertical(delta)

    fun moveHorizontalActive(delta: Int): String? = active.moveHorizontal(delta)
}
