package com.psplauncher.feature.xmb.viewmodel

enum class SearchScope(
    val label: String,
    val hint: String,
    val emptyTitle: String,
    val emptyHint: String,
) {
    ALL(

        "Search", "Games, apps, music, video, photos and books",
        "Nothing to search yet", "Add a library in Settings, then search from anywhere",
    ),
    GAMES(
        "Search Games", "Titles in your game library",
        "No games yet", "Add a ROM root in Settings ▸ Library ▸ Library Manager",
    ),
    VIDEOS(
        "Search Video", "Titles in your video libraries",
        "No videos yet", "Set a root folder in Settings ▸ Media ▸ Video",
    ),
    PHOTOS(
        "Search Photos", "File names in your albums",
        "No photos yet", "Set a root folder in Settings ▸ Media ▸ Photo",
    ),
    BOOKS(
        "Search Books", "Titles, authors and series",
        "No books yet", "Set a root folder in Settings ▸ Media ▸ Books",
    ),
    MUSIC(
        "Search Music", "Titles, artists and albums",
        "No music yet", "Set a root folder in Settings ▸ Media ▸ Music",
    ),
}

fun normalizeForSearch(text: String): String =
    buildString(text.length) {
        var lastWasSpace = true
        for (ch in text) {
            if (ch.isLetterOrDigit()) {
                append(ch.lowercaseChar())
                lastWasSpace = false
            } else if (!lastWasSpace) {
                append(' ')
                lastWasSpace = true
            }
        }
    }.trim()

fun matchesSearch(query: String, vararg fields: String?): Boolean {
    val terms = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
    if (terms.isEmpty()) return false
    val haystack = fields.filterNotNull().joinToString(" ") { normalizeForSearch(it) }

    return terms.all { haystack.contains(it) }
}

enum class SearchEmptyState { LOADING, EMPTY_LIBRARY, PROMPT, NO_MATCHES }

fun searchEmptyState(loaded: Boolean, query: String, anyContent: Boolean = true): SearchEmptyState = when {
    !loaded -> SearchEmptyState.LOADING

    !anyContent -> SearchEmptyState.EMPTY_LIBRARY
    normalizeForSearch(query).isEmpty() -> SearchEmptyState.PROMPT
    else -> SearchEmptyState.NO_MATCHES
}
