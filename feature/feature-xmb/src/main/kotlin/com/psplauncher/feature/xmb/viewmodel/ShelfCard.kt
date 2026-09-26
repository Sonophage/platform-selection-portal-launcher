package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.PlayState

sealed interface ShelfCard {
    val title: String

    val count: Int

    val cardId: String

    data class Favorites(override val count: Int) : ShelfCard {
        override val title get() = "Favorites"
        override val cardId get() = SHELF_FAVORITES_ID
    }

    data class Marked(val state: PlayState, override val count: Int) : ShelfCard {
        override val title get() = state.label
        override val cardId get() = "$SHELF_MARKED_PREFIX${state.name}"
    }

    data class RecentlyAdded(override val count: Int) : ShelfCard {
        override val title get() = "Recently Added"
        override val cardId get() = SHELF_RECENT_ID
    }
}

const val SHELF_FAVORITES_ID = "__shelf_favorites__"
const val SHELF_RECENT_ID = "__shelf_recent__"
const val SHELF_MARKED_PREFIX = "__shelf_marked_"

fun shelfCardFor(cardId: String?): ShelfCard? = when {
    cardId == null -> null
    cardId == SHELF_FAVORITES_ID -> ShelfCard.Favorites(0)
    cardId == SHELF_RECENT_ID -> ShelfCard.RecentlyAdded(0)
    cardId.startsWith(SHELF_MARKED_PREFIX) ->
        PlayState.fromName(cardId.removePrefix(SHELF_MARKED_PREFIX))?.let { ShelfCard.Marked(it, 0) }
    else -> null
}

val SHELF_CARD_IDS: Set<String> =
    setOf(SHELF_FAVORITES_ID, SHELF_RECENT_ID) +
        PlayState.entries.map { "$SHELF_MARKED_PREFIX${it.name}" }.toSet()
