package com.psplauncher.feature.xmb.viewmodel

// ── Library search ────────────────────────────────────────────────────────────
//
// One surface, two ways in: a Search row at the top of a library searches only that library, and
// the Select button searches everything. The scope is the only difference between them, which is
// why there is one of these rather than five.
//
// The matching rule is here, on its own and pure, because it is the part that is easy to get
// subtly wrong and impossible to notice: a rule that is slightly too strict returns nothing for a
// search the user is sure should work, and says nothing about why.

/**
 * Which library a search looks in.
 *
 * [emptyTitle] and [emptyHint] are what the surface says when there is nothing to search at all,
 * which is a different sentence from "nothing matched" and has to name the place the user fills.
 * A search that answers "No matches" on a library that has never held anything sends them looking
 * in Library Manager for a file they never added.
 */
enum class SearchScope(
    val label: String,
    val hint: String,
    val emptyTitle: String,
    val emptyHint: String,
) {
    ALL(
        "Search", "Games, video, photos and books",
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
}

/**
 * Folds a string down to what a search should compare (pure — unit-tested).
 *
 * Lowercase, and every run of non-alphanumeric characters becomes one space. That is what makes
 * "spider-man" find "Spider Man", "Zelda: Ocarina" find "Zelda - Ocarina", and a file called
 * `THE_LEGEND.mp4` findable by typing "legend". Punctuation in titles is decoration, and a user
 * typing into a search box does not reproduce it.
 */
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

/**
 * Whether [query] matches any of [fields] (pure — unit-tested).
 *
 * EVERY word of the query must appear somewhere across the fields, in any order: "ocarina zelda"
 * finds "The Legend of Zelda: Ocarina of Time". Words rather than the whole string, because a
 * user typing two words they remember is the common case and demanding their original order is
 * the sort of strictness that returns nothing and explains nothing.
 *
 * A blank query matches NOTHING, not everything. The search surface shows a "type to search" hint
 * on an empty box rather than dumping a 147-game library into it, and it can only do that if the
 * empty case is distinguishable here.
 */
fun matchesSearch(query: String, vararg fields: String?): Boolean {
    val terms = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
    if (terms.isEmpty()) return false
    val haystack = fields.filterNotNull().joinToString(" ") { normalizeForSearch(it) }
    // No empty-haystack guard: an entry with nothing to match on fails the all-terms rule on its
    // own, because an empty string contains no non-empty term. One was written here and deleted
    // when breaking it on purpose changed no test -- it was decoration.
    return terms.all { haystack.contains(it) }
}

/** Why a search list is empty. Four different things, and they must not be confused. */
enum class SearchEmptyState { LOADING, EMPTY_LIBRARY, PROMPT, NO_MATCHES }

/**
 * What an empty result list is actually saying (pure — unit-tested).
 *
 * The order is the point. A search opens, the libraries are still being read, and the query is
 * blank: all three conditions are true at once, and only one of the three answers is honest.
 * Saying "No matches" before anything has been read is a claim about a library that has not been
 * looked at, and it is indistinguishable on screen from a real empty result -- the user concludes
 * their game is missing and goes looking for it in Library Manager.
 */
fun searchEmptyState(loaded: Boolean, query: String, anyContent: Boolean = true): SearchEmptyState = when {
    !loaded -> SearchEmptyState.LOADING
    // Nothing to search is not the same as nothing matching, and it is the whole argument above
    // applied one step further out. This branch was missing: with an empty library, typing three
    // letters got "No matches — nothing here matches that", which is a statement about a library
    // that has never had anything in it. The user goes looking for a file they never added.
    //
    // It sits above the blank-query check on purpose. An empty library with an empty query should
    // say the library is empty, not invite a search that cannot succeed.
    !anyContent -> SearchEmptyState.EMPTY_LIBRARY
    normalizeForSearch(query).isEmpty() -> SearchEmptyState.PROMPT
    else -> SearchEmptyState.NO_MATCHES
}
