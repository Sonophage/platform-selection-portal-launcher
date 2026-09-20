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

/** Which library a search looks in. */
enum class SearchScope(val label: String, val hint: String) {
    ALL("Search", "Games, video, photos and books"),
    GAMES("Search Games", "Titles in your game library"),
    VIDEOS("Search Video", "Titles in your video libraries"),
    PHOTOS("Search Photos", "File names in your albums"),
    BOOKS("Search Books", "Titles, authors and series"),
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

/** Why a search list is empty. Three different things, and they must not be confused. */
enum class SearchEmptyState { LOADING, PROMPT, NO_MATCHES }

/**
 * What an empty result list is actually saying (pure — unit-tested).
 *
 * The order is the point. A search opens, the libraries are still being read, and the query is
 * blank: all three conditions are true at once, and only one of the three answers is honest.
 * Saying "No matches" before anything has been read is a claim about a library that has not been
 * looked at, and it is indistinguishable on screen from a real empty result -- the user concludes
 * their game is missing and goes looking for it in Library Manager.
 */
fun searchEmptyState(loaded: Boolean, query: String): SearchEmptyState = when {
    !loaded -> SearchEmptyState.LOADING
    normalizeForSearch(query).isEmpty() -> SearchEmptyState.PROMPT
    else -> SearchEmptyState.NO_MATCHES
}
