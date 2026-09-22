package com.psplauncher.feature.xmb.viewmodel

/**
 * What the home shelf is showing.
 *
 * ALL first, then the four media in the owner's order. The order is the cycle order: X steps
 * through it and wraps, so it is the one place that decides both what the label says and where
 * the next press lands.
 */
enum class RecentFilter(val label: String) {
    ALL("All"),
    GAMES("Games"),
    MUSIC("Music"),
    BOOKS("Books"),
    VIDEO("Video");

    /** The next filter in the cycle, wrapping past the end back to [ALL]. */
    fun next(): RecentFilter = entries[(ordinal + 1) % entries.size]
}

/**
 * The home shelf: four libraries merged into one list, newest first.
 *
 * Takes the four media as separate arguments rather than one pre-tagged list. A single list would
 * need every caller to label each row with its own kind, and a row labelled [RecentFilter.ALL] —
 * or labelled as the wrong medium — would be silently unfilterable. Here the shape of the call
 * makes that impossible, and the `when` below is exhaustive over the enum, so adding a fifth
 * medium fails to compile until this function has been told what to do with it.
 *
 * Each list is pairs of (recency stamp, row). The stamp is what the merge orders by and is not
 * carried on the row itself: an XMBItem is what the UI draws, and four media disagree about what
 * their timestamp column is even called.
 *
 * [limit] is applied AFTER the filter, so narrowing to one medium shows that medium's newest
 * [limit] rather than whatever survived a cut made across all four.
 */
internal fun mergeRecents(
    games: List<Pair<Long, XMBItem>>,
    music: List<Pair<Long, XMBItem>>,
    books: List<Pair<Long, XMBItem>>,
    videos: List<Pair<Long, XMBItem>>,
    filter: RecentFilter,
    limit: Int,
): List<XMBItem> {
    val chosen = when (filter) {
        RecentFilter.ALL -> games + music + books + videos
        RecentFilter.GAMES -> games
        RecentFilter.MUSIC -> music
        RecentFilter.BOOKS -> books
        RecentFilter.VIDEO -> videos
    }
    return chosen
        .sortedByDescending { it.first }
        .take(limit)
        .map { it.second }
}
