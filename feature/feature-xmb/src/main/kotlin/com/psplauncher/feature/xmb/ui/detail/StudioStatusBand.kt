package com.psplauncher.feature.xmb.ui.detail

// ── What the Artwork Studio is not telling you ────────────────────────────────
//
// The Studio is four zones — query, destination tabs, sources, grid — and every one of them says
// where you ARE. None says how you are DOING. To learn how much of a game's artwork is filled you
// walk eleven tabs; to learn a source returned nothing you visit it; and to learn you are about to
// run out of ScreenScraper requests you hit the cap, because the daily counter was parsed on every
// response and shown on none.
//
// Three counters, each a value against a limit, fixed above the content so they do not scroll away
// mid-run. Borrowed in shape from NeoStation's scraping panel, where quota sits as a peer of
// progress rather than on an account page.

/** One counter: a label, what it reads, and how full it is. [fraction] is null when there is no cap. */
data class StudioStat(
    val label: String,
    val value: String,
    val fraction: Float?,
)

/**
 * The three numbers the Studio's status band shows (pure — unit-tested).
 *
 * [filledKinds] and [totalKinds] describe the GAME, not the visible tab, which is the point: the
 * tab row already tells you where you are standing.
 *
 * [requestsToday] and [dailyCap] come from ScreenScraper's `ssuser` block and are null until an
 * authenticated response has carried one. A null cap shows the count alone rather than inventing a
 * denominator — an unknown limit is not an unlimited one, and a bar drawn against a guess would be
 * worse than no bar.
 */
fun studioStats(
    filledKinds: Int,
    totalKinds: Int,
    foundResults: Int,
    resultsLoading: Boolean,
    requestsToday: Int?,
    dailyCap: Int?,
): List<StudioStat> = listOf(
    StudioStat(
        label = "Filled",
        value = "$filledKinds / $totalKinds",
        fraction = safeFraction(filledKinds, totalKinds),
    ),
    StudioStat(
        label = "Found",
        // A loading source reads as a dash, never as zero: "no artwork here" and "not asked yet"
        // are different answers and only one of them is a reason to switch source.
        value = if (resultsLoading) "…" else foundResults.toString(),
        fraction = null,
    ),
    StudioStat(
        label = "Requests",
        value = when {
            requestsToday == null -> "—"
            dailyCap == null || dailyCap <= 0 -> requestsToday.toString()
            else -> "$requestsToday / $dailyCap"
        },
        fraction = if (requestsToday != null && dailyCap != null) safeFraction(requestsToday, dailyCap) else null,
    ),
)

/** Guards the two ways a ratio goes wrong: a zero denominator, and a count past its own cap. */
private fun safeFraction(value: Int, total: Int): Float? {
    if (total <= 0) return null
    return (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
