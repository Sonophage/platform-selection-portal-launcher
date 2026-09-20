package com.psplauncher.feature.xmb.viewmodel

// ── Quick Search ──────────────────────────────────────────────────────────────
//
// The Network column's search box. PSPLauncher never picks a browser: it hands Android an intent
// and lets the user's own default answer it, which is why there is no browser setting anywhere in
// this app and no in-app browser in the plan.
//
// The one real decision is what a typed string MEANS. "cave story" is obviously a search.
// "news.bbc.co.uk" typed into a search intent searches the web for that string, which on a
// handheld with a keyboard you did not want to use twice is a small insult. So the rule below
// tells them apart, and it is a pure function because the interesting part is the edge cases and
// the boring part is the Intent.

/** What Quick Search should do with what was typed. */
sealed interface QuickSearchAction {
    /** Search the web. */
    data class Search(val query: String) : QuickSearchAction

    /** Open this as an address. Always carries a scheme, so the caller never has to add one. */
    data class Open(val url: String) : QuickSearchAction

    /** Nothing worth doing: an empty box, or only whitespace. */
    data object None : QuickSearchAction
}

/**
 * Decides whether [raw] is an address or a search (pure — unit-tested).
 *
 * An explicit scheme is taken at its word. Otherwise it is an address only if it looks like one
 * from every angle at once: no spaces, a dot with something on both sides, and a last segment
 * that is letters only and at least two of them. That last part is what keeps "3.5" and
 * "half-life 2.exe" out, and it is deliberately not a list of real top-level domains -- such a
 * list is wrong the week it is written, and the cost of guessing wrong here is one search result
 * page rather than anything lost.
 */
fun quickSearchActionFor(raw: String): QuickSearchAction {
    val text = raw.trim()
    if (text.isEmpty()) return QuickSearchAction.None
    if (text.startsWith("http://", ignoreCase = true) || text.startsWith("https://", ignoreCase = true)) {
        return QuickSearchAction.Open(text)
    }
    if (looksLikeHost(text)) return QuickSearchAction.Open("https://$text")
    return QuickSearchAction.Search(text)
}

private fun looksLikeHost(text: String): Boolean {
    if (text.any { it.isWhitespace() }) return false
    // Ignore a path or query when judging the host: "bbc.co.uk/news" is still an address.
    val host = text.substringBefore('/').substringBefore('?')
    val labels = host.split('.')
    if (labels.size < 2) return false
    if (labels.any { it.isEmpty() }) return false
    val tld = labels.last()
    return tld.length >= 2 && tld.all { it.isLetter() }
}
