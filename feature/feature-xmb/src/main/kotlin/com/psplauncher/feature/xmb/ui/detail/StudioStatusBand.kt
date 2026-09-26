package com.psplauncher.feature.xmb.ui.detail

data class StudioStat(
    val label: String,
    val value: String,
    val fraction: Float?,
)

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

private fun safeFraction(value: Int, total: Int): Float? {
    if (total <= 0) return null
    return (value.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
