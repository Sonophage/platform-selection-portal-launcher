package com.psplauncher.feature.xmb.viewmodel

sealed interface QuickSearchAction {
    data class Search(val query: String) : QuickSearchAction

    data class Open(val url: String) : QuickSearchAction

    data object None : QuickSearchAction
}

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

    val host = text.substringBefore('/').substringBefore('?')
    val labels = host.split('.')
    if (labels.size < 2) return false
    if (labels.any { it.isEmpty() }) return false
    val tld = labels.last()
    return tld.length >= 2 && tld.all { it.isLetter() }
}
