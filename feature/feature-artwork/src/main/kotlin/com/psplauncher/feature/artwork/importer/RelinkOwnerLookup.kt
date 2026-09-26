package com.psplauncher.feature.artwork.importer

internal object RelinkOwnerLookup {
    fun owners(
        platformId: String,
        kind: String,
        fileName: String,
        fileStem: String,
        baseStem: String,
        claims: Map<Triple<String, String, String>, Long>,
        recordOwners: Map<Triple<String, String, String>, Set<Long>>,
        fuzzyMatch: (String) -> List<Long>?,
        identityOwners: (String) -> List<Long>? = { null },
    ): List<Long>? {
        fun claimed(stem: String): List<Long>? = claims[Triple(platformId, kind, stem.lowercase())]?.let { listOf(it) }
        fun recorded(stem: String): List<Long>? = recordOwners[Triple(platformId, kind, stem.lowercase())]?.toList()
        fun identified(stem: String): List<Long>? = identityOwners(stem)?.takeIf { it.isNotEmpty() }
        val hasBase = baseStem != fileStem
        return identified(fileStem)
            ?: (if (hasBase) identified(baseStem) else null)
            ?: claimed(fileStem)
            ?: recorded(fileStem)
            ?: (if (hasBase) claimed(baseStem) else null)
            ?: fuzzyMatch(fileName)
            ?: if (hasBase) {
                recorded(baseStem) ?: fuzzyMatch("$baseStem.${fileName.substringAfterLast('.')}")
            } else {
                null
            }
    }
}
