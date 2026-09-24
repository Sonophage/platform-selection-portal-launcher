package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.PlayState

/**
 * One shelf in the Shelves column.
 *
 * A shelf is a cut of the library nobody had to make: Favorites, the three marks, and whatever
 * arrived recently. User-made collections are NOT here — those are built by hand and live behind
 * Add to Collection, which is why this column is not called Collections.
 *
 * Every shelf carries its own count, and a shelf with a count of zero is never constructed at all.
 * That is the whole hiding rule, in one place: there is no "is it empty" branch downstream because
 * an empty shelf does not exist to ask about.
 */
sealed interface ShelfCard {

    /** What the card is titled and what the drill-in is titled. One string, so they cannot differ. */
    val title: String

    /** How many games are on it. Always greater than zero — see the class docs. */
    val count: Int

    /** The id the crossbar uses to open it, in the same namespace as the Games root's own cards. */
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

/**
 * The shelf a card id names, or null when the id is not a shelf's.
 *
 * Parsed rather than stored, for the reason the pill row learned this morning: the thing that
 * opens a shelf and the thing that fills it have to agree about WHICH shelf, and an id both sides
 * read is one fact. A mark the enum no longer has resolves to null, so a stale id opens nothing
 * instead of opening the wrong thing.
 */
fun shelfCardFor(cardId: String?): ShelfCard? = when {
    cardId == null -> null
    cardId == SHELF_FAVORITES_ID -> ShelfCard.Favorites(0)
    cardId == SHELF_RECENT_ID -> ShelfCard.RecentlyAdded(0)
    cardId.startsWith(SHELF_MARKED_PREFIX) ->
        PlayState.fromName(cardId.removePrefix(SHELF_MARKED_PREFIX))?.let { ShelfCard.Marked(it, 0) }
    else -> null
}

/**
 * Every id a shelf card can carry, for the confirm dispatch to match on.
 *
 * Derived from the enum rather than listed, so a new mark cannot be added without its shelf
 * becoming openable — the pair that would otherwise draw a card nothing could open.
 */
val SHELF_CARD_IDS: Set<String> =
    setOf(SHELF_FAVORITES_ID, SHELF_RECENT_ID) +
        PlayState.entries.map { "$SHELF_MARKED_PREFIX${it.name}" }.toSet()
