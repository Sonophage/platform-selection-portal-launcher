package com.psplauncher.feature.library.scanner

fun cueSheetReferencesRaw(lines: List<String>): List<String> {
    val refs = mutableListOf<String>()
    for (line in lines) {
        val trimmed = line.trim()
        if (!trimmed.startsWith("FILE", ignoreCase = true)) continue
        val name = if ('"' in trimmed) {
            val start = trimmed.indexOf('"')
            val end = trimmed.lastIndexOf('"')
            if (end > start) trimmed.substring(start + 1, end) else null
        } else {
            trimmed.removePrefix("FILE").trim().substringBefore(' ').takeIf { it.isNotEmpty() }
        }
        name?.let { refs.add(it.substringAfterLast('/').substringAfterLast('\\')) }
    }
    return refs
}

fun gdiSheetTrackNamesRaw(lines: List<String>): List<String> {
    val tracks = mutableListOf<String>()
    for (line in lines) {
        val fields = Regex("\"(?:\\\\.|[^\"])*\"|\\S+")
            .findAll(line.trim()).map { it.value }.toList()
        if (fields.size < 5) continue
        val name = fields[4].removePrefix("\"").removeSuffix("\"")
        if (name.isNotEmpty()) tracks.add(name.substringAfterLast('/').substringAfterLast('\\'))
    }
    return tracks
}

fun cueSheetReferences(lines: List<String>): Set<String> =
    cueSheetReferencesRaw(lines).map { it.lowercase() }.toSet()

fun gdiSheetTrackNames(lines: List<String>): Set<String> =
    gdiSheetTrackNamesRaw(lines).map { it.lowercase() }.toSet()
