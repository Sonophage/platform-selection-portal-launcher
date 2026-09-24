package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.Game

/** Covers in the fan. Three, as 1a draws it — one centred and one leaning each way. */
const val FAN_COVER_COUNT = 3

/** Covers in a card's art grid. Four, in two rows of two. */
const val GRID_COVER_COUNT = 4

/** How many to carry on an item: enough for whichever consumer wants the most. */
const val INSIDE_COVER_COUNT = 4

/**
 * The newest covers inside a card, newest first.
 *
 * ONE list, two consumers: the fan on the right takes three and the card's art grid takes four.
 * Computing it once at the widest count and letting each take what it needs beats two queries
 * that could disagree about which games are newest.
 *
 * "Newest" is the added-date, then the id. [Game.dateAdded] is a real stamp now, written once when
 * the row first enters the library and preserved across every rescan.
 *
 * THE ID IS STILL THE TIE-BREAK, and it carries the whole of an existing library. Migration 51 to
 * 52 set every row that predates the column to 0 rather than to the migration's own instant, so on
 * a library that has not been rescanned every game ties at 0 and the fan falls through to exactly
 * the id order it used before. Nothing reorders on upgrade; rows added from here on sort above
 * them, correctly, and a platform deleted and re-added no longer jumps to the front on the
 * strength of new ids alone.
 *
 * SORTED, THEN MAPPED, THEN TAKEN, and the order of those three is the whole function. Taking
 * first would hand back the three newest games and then silently drop the ones with no artwork,
 * so a card whose two newest games are unscraped would draw a one-card "fan" while sitting on
 * fifty covers. Mapping first means the three newest games THAT HAVE ART, which is what the fan
 * is for.
 */
fun fanCoversOf(games: List<Game>, limit: Int = INSIDE_COVER_COUNT): List<String> = games
    .sortedWith(compareByDescending<Game> { it.dateAdded ?: 0L }.thenByDescending { it.id })
    .mapNotNull { it.boxArtUri ?: it.artworkUri }
    .take(limit)

/**
 * How many covers a media column keeps ready: four per row, for as many rows as a column has.
 *
 * Six rows' worth. The Music root is the longest at five sections plus Now Playing, and a column
 * that outgrows this simply repeats nothing — the rows past the pool get no grid rather than a
 * wrong one.
 */
const val MEDIA_COVER_POOL = GRID_COVER_COUNT * 6

/**
 * One row's four covers out of a column's shared pool, offset by its position.
 *
 * THE OFFSET IS THE POINT. A media column's rows are different cuts of the same library — Songs,
 * Artists, Albums, Playlists — so handing each of them "the four newest covers" would put the
 * SAME four on every row, each claiming to stand for something different. Sliding the window by
 * four per row makes every row a different handful of the library, which is what a sampling of
 * your own collection should look like.
 *
 * Past the end of the pool this returns empty, and an empty list draws no grid at all. A row with
 * the glyph it always had is a better answer than a row showing covers that belong to the row
 * above it.
 */
fun List<String>.gridSliceAt(index: Int): List<String> =
    drop(index * GRID_COVER_COUNT).take(GRID_COVER_COUNT)
