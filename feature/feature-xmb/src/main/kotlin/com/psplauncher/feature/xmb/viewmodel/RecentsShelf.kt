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
    VIDEO("Video"),

    /**
     * Android apps, and the only filter that can be switched off.
     *
     * Off by default: the shelf is the screen the launcher opens on, and the most recently used
     * thing on a device with a hundred and fifty apps is very often one you opened for ten
     * seconds — which would push the game you were actually playing off your own home screen.
     */
    APPS("Apps");

    companion object {
        /**
         * The filters that exist right now, in cycle order.
         *
         * ONE list, read by the X button and by the row of names a finger taps. Two would be the
         * pair that disagrees the first time a filter is added — and the pad would then cycle onto
         * a name the screen never draws, or the screen would offer one the pad skips.
         */
        fun visible(includeApps: Boolean): List<RecentFilter> =
            entries.filter { it != APPS || includeApps }
    }

    /**
     * The next filter in the cycle, wrapping past the end back to [ALL].
     *
     * Skips anything switched off, and falls back to [ALL] if the current filter has just been
     * switched off underneath the cursor — which happens the moment someone turns apps off while
     * standing on the Apps filter.
     */
    fun next(includeApps: Boolean): RecentFilter {
        val cycle = visible(includeApps)
        val here = cycle.indexOf(this)
        return if (here < 0) ALL else cycle[(here + 1) % cycle.size]
    }
}

/**
 * The home shelf: the libraries merged into one list, newest first.
 *
 * Takes each medium as its own argument rather than one pre-tagged list. A single list would
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
    /**
     * Android apps, already empty when the setting is off.
     *
     * Emptied by the caller rather than filtered here, so ALL and APPS cannot disagree about
     * whether apps are on: one list that is empty means both say no.
     */
    apps: List<Pair<Long, XMBItem>>,
    filter: RecentFilter,
    limit: Int,
): List<XMBItem> {
    val chosen = when (filter) {
        RecentFilter.ALL -> games + music + books + videos + apps
        RecentFilter.GAMES -> games
        RecentFilter.MUSIC -> music
        RecentFilter.BOOKS -> books
        RecentFilter.VIDEO -> videos
        RecentFilter.APPS -> apps
    }
    return chosen
        .sortedByDescending { it.first }
        .take(limit)
        .map { it.second }
}
