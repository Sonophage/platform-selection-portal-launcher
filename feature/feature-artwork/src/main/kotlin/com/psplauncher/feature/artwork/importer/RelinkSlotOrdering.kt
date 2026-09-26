package com.psplauncher.feature.artwork.importer

internal object RelinkSlotOrdering {
    fun <T> order(
        files: List<T>,
        stemOf: (T) -> String,
        ordinalOf: (T) -> Int,
        priorSortOrder: (String) -> Int?,
    ): List<T> {
        val withPrior = mutableListOf<Pair<Int, T>>()
        val withoutPrior = mutableListOf<T>()
        for (file in files) {
            val prior = priorSortOrder(stemOf(file).lowercase())
            if (prior != null) withPrior += prior to file else withoutPrior += file
        }
        return withPrior.sortedBy { it.first }.map { it.second } + withoutPrior.sortedBy(ordinalOf)
    }
}
