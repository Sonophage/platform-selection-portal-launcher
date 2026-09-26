package com.psplauncher.feature.xmb.ui.detail

import com.psplauncher.core.domain.model.GamepadAction
import com.psplauncher.core.navigation.NavigationCommand
import com.psplauncher.core.navigation.NavigationDirection
import com.psplauncher.core.navigation.NavigationEngine
import com.psplauncher.core.navigation.NavigationLogger
import com.psplauncher.core.navigation.NavigationNode
import com.psplauncher.core.navigation.NavigationTouchAction

internal fun mediaStableId(media: DetailMedia): String =
    if (media.isVideo) "v:${media.uri}" else "i:${media.uri}"

object GameDetailKeys {
    const val LAUNCH = "game-detail:launch"
    const val ACTIONS = "game-detail:actions"

    const val DETAILS = "game-detail:details"

    const val FAVORITE = "game-detail:favorite"
    const val OPTIONS = "game-detail:options-button"
    const val DISCS = "game-detail:discs"
    const val OVERVIEW = "game-detail:overview"
    const val INFO = "game-detail:info"
    const val MEDIA = "game-detail:media"

    fun disc(gameId: Long): String = "game-detail:disc:$gameId"

    fun media(stableId: String): String = "game-detail:media:$stableId"

    fun option(actionName: String): String = "game-detail:options:$actionName"

    fun detailsRow(actionName: String): String = "game-detail:details:$actionName"

    fun emulatorPick(profileId: String): String = "game-detail:emulator-pick:$profileId"

    fun collectionRow(collectionId: Long): String = "game-detail:collection:$collectionId"

    const val COLLECTION_CREATE_ROW = "game-detail:collection:create"

    const val METADATA_APPLY = "game-detail:metadata:apply"

    fun metadataField(fieldName: String): String = "game-detail:metadata:field:$fieldName"

    const val CONFIRM_REMOVE = "game-detail:confirm-remove"
    const val CONFIRM_CANCEL = "game-detail:confirm-cancel"

    const val MODAL_DETAILS = "game-detail:modal:details"
    const val MODAL_OPTIONS = "game-detail:modal:options"
    const val MODAL_EMULATOR_PICKER = "game-detail:modal:emulator-picker"
    const val MODAL_COLLECTION_PICKER = "game-detail:modal:collection-picker"
    const val MODAL_METADATA = "game-detail:modal:metadata"
    const val MODAL_CONFIRM_REMOVE = "game-detail:modal:confirm-remove"
    const val MODAL_NOTE_EDITOR = "game-detail:modal:note-editor"
    const val MODAL_TITLE_EDITOR = "game-detail:modal:title-editor"
    const val MODAL_MANUAL_VIEWER = "game-detail:modal:manual"
    const val MODAL_IMAGE_VIEWER = "game-detail:modal:image-viewer"
    const val MODAL_VIDEO_PLAYER = "game-detail:modal:video-player"
    const val MODAL_ARTWORK_STUDIO = "game-detail:modal:artwork-studio"
}

data class GameDetailNavContent(

    val gameId: Long = 0L,
    val loaded: Boolean = false,

    val showEmulatorControls: Boolean = false,

    val discIds: List<Long> = emptyList(),

    val mediaIds: List<String> = emptyList(),

    val onMediaPage: Boolean = false,
)

class GameDetailNav(
    private val logger: NavigationLogger = NavigationLogger.NONE,
) {
    private val engine = NavigationEngine(contextId = "game-detail", logger = logger)

    var onActivate: (String) -> Unit = {}

    private var content = GameDetailNavContent()

    private var geometry: Map<String, Float> = emptyMap()

    private var registeredNodes: List<NavigationNode> = emptyList()

    private var openingFocusPlaced = false

    private val autoEnterRows = setOf(GameDetailKeys.ACTIONS, GameDetailKeys.DISCS, GameDetailKeys.MEDIA)

    val focusedKey: String? get() = engine.focusedKey
    val cursorVisible: Boolean get() = engine.cursorVisible
    val acceptsInput: Boolean get() = engine.acceptsInput
    val isModalActive: Boolean get() = engine.isModalActive
    val activeContextId: String get() = engine.activeContextId

    fun reachableKeys(): Set<String> = registeredNodes
        .flatMap { node ->
            buildList {
                if (node.focusable && node.enabled) add(node.key)
                node.children.filter { it.focusable && it.enabled }.forEach { add(it.key) }
            }
        }
        .toSet()

    fun updateContent(newContent: GameDetailNavContent) {
        content = newContent
        if (engine.isModalActive) return
        registerPageGraph()
    }

    fun reportGeometry(map: Map<String, Float>) {
        if (map.isEmpty()) return
        geometry = geometry + map
        if (engine.isModalActive) return
        registerPageGraph()
    }

    private fun registerPageGraph() {
        registeredNodes = pageNodes()

        engine.replaceNodesWithGeometry(registeredNodes, geometry, geometry)
        placeOpeningFocus()
        normalizeFocus()
    }

    private fun placeOpeningFocus() {
        if (openingFocusPlaced) return
        if (engine.focusedKey == null) return
        openingFocusPlaced = true
        engine.setFocused(GameDetailKeys.LAUNCH)
    }

    private fun normalizeFocus() {
        val key = engine.focusedKey ?: return
        if (key !in autoEnterRows) return
        childKeys(key).firstOrNull()?.let { engine.setFocused(it) }
    }

    private fun pageNodes(): List<NavigationNode> {
        if (!content.loaded) return emptyList()
        val nodes = mutableListOf<NavigationNode>()

        nodes += container(
            GameDetailKeys.ACTIONS,
            listOf(
                NavigationNode(GameDetailKeys.FAVORITE, onSelect = { activate(GameDetailKeys.FAVORITE) }),
                NavigationNode(GameDetailKeys.OPTIONS, onSelect = { activate(GameDetailKeys.OPTIONS) }),
                NavigationNode(GameDetailKeys.LAUNCH, onSelect = { activate(GameDetailKeys.LAUNCH) }),
            ),
        )
        if (content.discIds.size > 1) {
            nodes += container(
                GameDetailKeys.DISCS,
                content.discIds.map { discId ->
                    NavigationNode(GameDetailKeys.disc(discId), onSelect = { activate(GameDetailKeys.disc(discId)) })
                },
            )
        }
        if (content.onMediaPage && content.mediaIds.isNotEmpty()) {
            nodes += container(
                GameDetailKeys.MEDIA,
                content.mediaIds.map { id ->
                    NavigationNode(GameDetailKeys.media(id), onSelect = { activate(GameDetailKeys.media(id)) })
                },
            )
        }
        return nodes
    }

    private fun container(
        key: String,
        children: List<NavigationNode>,
        selectable: Boolean = false,
    ): NavigationNode = NavigationNode(
        key = key,

        selectable = selectable,
        children = children,
    )

    private fun activate(key: String) {
        onActivate(key)
    }

    fun markReady() = engine.markReady()

    fun handleAction(action: GamepadAction): String? = when (action) {
        GamepadAction.NAVIGATE_UP -> move(NavigationDirection.UP)
        GamepadAction.NAVIGATE_DOWN -> move(NavigationDirection.DOWN)
        GamepadAction.NAVIGATE_LEFT -> move(NavigationDirection.LEFT)
        GamepadAction.NAVIGATE_RIGHT -> move(NavigationDirection.RIGHT)
        GamepadAction.SELECT -> {
            engine.dispatch(NavigationCommand.Confirm)
            engine.focusedKey
        }
        else -> engine.focusedKey
    }

    private fun move(direction: NavigationDirection): String? {
        val previousChildIndex = childIndex(engine.focusedKey)
        val landed = engine.dispatch(NavigationCommand.Direction(direction))

            ?: return engine.focusedKey

        if (landed !in autoEnterRows) return landed
        val children = childKeys(landed)
        if (children.isEmpty()) return landed
        val target = when (direction) {
            NavigationDirection.UP, NavigationDirection.DOWN ->
                children[if (previousChildIndex >= 0) previousChildIndex.coerceAtMost(children.lastIndex) else 0]
            NavigationDirection.LEFT, NavigationDirection.RIGHT -> children.first()
        }
        engine.setFocused(target)
        return engine.focusedKey
    }

    private fun childIndex(key: String?): Int {
        if (key == null) return -1
        registeredNodes.forEach { node ->
            val index = node.children.indexOfFirst { it.key == key }
            if (index >= 0) return index
        }
        return -1
    }

    private fun childKeys(key: String): List<String> =
        registeredNodes.firstOrNull { it.key == key }
            ?.children
            ?.filter { it.focusable && it.enabled }
            ?.map { it.key }
            .orEmpty()

    fun touch(key: String): Boolean = engine.dispatchTouch(key, NavigationTouchAction.TAP)

    fun markTouchInput() = engine.markTouchInput()

    fun setFocused(key: String?) = engine.setFocused(key)

    fun pushModal(
        contextId: String,
        nodes: List<NavigationNode> = emptyList(),
        preferredFocus: String? = null,
    ) {
        engine.pushModal(contextId)
        registeredNodes = nodes

        engine.replaceNodes(nodes)
        if (preferredFocus != null) engine.setFocused(preferredFocus)
        if (engine.focusedKey == null) engine.focusFirst()
    }

    fun updateModalNodes(nodes: List<NavigationNode>, preferredFocus: String? = null) {
        val hadFocus = engine.focusedKey != null
        registeredNodes = nodes
        engine.replaceNodes(nodes)
        if (!hadFocus && preferredFocus != null) engine.setFocused(preferredFocus)
    }

    fun popModal(): String? {
        val restored = engine.popContext()

        engine.setFocused(restored)
        registerPageGraph()
        return engine.focusedKey
    }

    fun beginRecoveryLock() = engine.beginRecoveryLock()

    fun endRecoveryLock() = engine.endRecoveryLock()
}
