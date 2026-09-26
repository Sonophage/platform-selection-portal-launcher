package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.domain.model.Game

const val FAN_COVER_COUNT = 3

const val GRID_COVER_COUNT = 4

const val INSIDE_COVER_COUNT = 4

fun fanCoversOf(games: List<Game>, limit: Int = INSIDE_COVER_COUNT): List<String> = games
    .sortedWith(compareByDescending<Game> { it.dateAdded ?: 0L }.thenByDescending { it.id })
    .mapNotNull { it.boxArtUri ?: it.artworkUri }
    .take(limit)

internal fun fanCoversToDraw(insideCovers: List<String>, cardArtGrid: Boolean): List<String> =
    if (cardArtGrid) insideCovers.take(FAN_COVER_COUNT) else emptyList()

const val MEDIA_COVER_POOL = GRID_COVER_COUNT * 6

fun List<String>.gridSliceAt(index: Int): List<String> =
    drop(index * GRID_COVER_COUNT).take(GRID_COVER_COUNT)
