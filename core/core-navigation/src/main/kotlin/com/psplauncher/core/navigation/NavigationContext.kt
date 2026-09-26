package com.psplauncher.core.navigation

class NavigationContext(

    val id: String,

    val modal: Boolean = false,
) {
    internal var nodes: List<NavigationNode> = emptyList()
    private var geometry: Map<String, Float> = emptyMap()
    private var ready: Boolean = false

    var focusedKey: String? = null
        private set

    var editHandler: EditModeHandler? = null
        private set

    var isReady: Boolean
        get() = ready
        private set(value) { ready = value }

    fun markReady() {
        ready = true
    }

    fun nodesInRegistrationOrder(): List<NavigationNode> = nodes

    fun geometryFor(key: String): Float? = geometry[key]

    fun allGeometry(): Map<String, Float> = geometry

    fun reportGeometry(key: String, y: Float) {
        geometry = geometry + (key to y)
    }

    fun hasGeometry(): Boolean = geometry.isNotEmpty()

    fun focusedNode(): NavigationNode? = focusedKey?.let { findNode(it) }

    fun editHandlerFor(key: String): EditModeHandler? {
        if (editHandler != null) return null
        val node = findNode(key) ?: return null
        return node.onEditStart?.invoke()?.also { editHandler = it }
    }

    fun clearEditHandler() {
        editHandler?.onExit()
        editHandler = null
    }

    fun updateNodes(
        newNodes: List<NavigationNode>,
        newGeometry: Map<String, Float> = emptyMap(),
        previousGeometry: Map<String, Float> = geometry,
    ): String? {
        val previousNodes = nodes
        val previousKey = focusedKey
        nodes = newNodes
        if (newGeometry.isNotEmpty()) geometry = newGeometry

        val focusable = focusableNodes()
        val focusableKeys = focusable.flatMap { node -> listOf(node.key) + node.children.filter { it.focusable && it.enabled }.map { it.key } }

        focusedKey = when {
            previousKey != null && previousKey in focusableKeys -> previousKey
            previousKey != null -> recoverAfterRemoval(previousNodes, previousKey, previousGeometry, focusable)
            else -> focusable.firstOrNull()?.key
        }
        return focusedKey
    }

    private fun recoverAfterRemoval(
        previousNodes: List<NavigationNode>,
        previousKey: String,
        previousGeometry: Map<String, Float>,
        focusable: List<NavigationNode>,
    ): String? {
        if (focusable.isEmpty()) return null

        previousNodes.forEach { node ->
            if (node.children.any { it.key == previousKey } && focusable.any { it.key == node.key }) {
                return node.key
            }
        }

        val previousY = previousGeometry[previousKey]
        if (previousY != null && geometry.isNotEmpty()) {
            focusable.minByOrNull { key -> kotlin.math.abs((geometry[key.key] ?: Float.MAX_VALUE) - previousY) }
                ?.let { return it.key }
        }

        val previousIndex = previousNodes.indexOfFirst { it.key == previousKey }
        return if (previousIndex >= 0) {
            focusable.minByOrNull { kotlin.math.abs(nodes.indexOf(it) - previousIndex) }?.key
        } else {
            focusable.firstOrNull()?.key
        }
    }

    fun focusableNodes(): List<NavigationNode> {
        val focusable = nodes.filter { it.focusable && it.enabled }
        if (!hasGeometry()) return focusable
        return focusable.sortedBy { geometry[it.key] ?: Float.MAX_VALUE }
    }

    fun findNode(key: String): NavigationNode? =
        nodes.firstOrNull { it.key == key } ?: nodes.firstNotNullOfOrNull { node ->
            node.children.firstOrNull { it.key == key }
        }

    fun ownerOf(key: String): NavigationNode? =
        nodes.firstOrNull { node -> node.children.any { it.key == key } }

    fun isKnownKey(key: String): Boolean = findNode(key) != null

    fun moveVertical(delta: Int): String? {
        val focusable = focusableNodes()
        if (focusable.isEmpty()) {
            focusedKey = null
            return null
        }

        val owner = ownerOf(focusedKey ?: "")
        val baseKey = owner?.key ?: focusedKey
        val current = focusable.indexOfFirst { it.key == baseKey }
        val target = if (current < 0) 0 else (current + delta).coerceIn(0, focusable.lastIndex)
        val targetNode = focusable[target]

        focusedKey = targetNode.key
        return focusedKey
    }

    fun moveHorizontal(delta: Int): String? {
        val owner = ownerOf(focusedKey ?: "")
        val children = (owner ?: findNode(focusedKey ?: ""))?.children
            ?.filter { it.focusable && it.enabled }
            ?: return null
        if (children.isEmpty()) return null
        if (owner != null && focusedKey != owner.key) {
            val index = children.indexOfFirst { it.key == focusedKey }
            val targetIndex = index + delta
            focusedKey = when {
                targetIndex < 0 -> owner.key
                targetIndex >= children.size -> focusedKey
                else -> children[targetIndex].key
            }
        } else {
            focusedKey = if (delta > 0) children.first().key else focusedKey
        }
        return focusedKey
    }

    fun focusFirst(): String? {
        focusedKey = focusableNodes().firstOrNull()?.key
        return focusedKey
    }

    fun setFocused(key: String?) {
        if (key == null) {
            focusedKey = null
            return
        }
        if (isKnownKey(key)) {
            if (key != focusedKey) clearEditHandler()
            focusedKey = key
        }
    }

    fun confirm(): Boolean {
        val handler = editHandler
        if (handler != null) return handler.onConfirm()
        val key = focusedKey ?: return false
        val owner = ownerOf(key)
        val node = if (owner != null) {
            owner.children.firstOrNull { it.key == key } ?: return false
        } else {
            findNode(key) ?: return false
        }
        if (!node.focusable || !node.selectable || !node.enabled) return false
        return node.onSelect?.invoke() != null
    }

    internal fun setFocusedKeyForTest(key: String?) {
        focusedKey = key
    }
}
