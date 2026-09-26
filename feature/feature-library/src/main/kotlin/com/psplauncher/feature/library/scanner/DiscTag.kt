package com.psplauncher.feature.library.scanner

data class DiscTag(
    val discNumber: Int,
    val discTotal: Int?,

    val strippedTitle: String,
)

private val TAG_IN_GROUP = Regex(
    """[(\[](?:[^()\[\]]*?)(?:disc|disk|cd)\s*(\d+)(?:\s*of\s*(\d+))?[^()\[\]]*?[)\]]""",
    RegexOption.IGNORE_CASE,
)

private val TRAILING_TAG = Regex(
    """[-–—]\s*(?:disc|disk)\s*(\d+)(?:\s*of\s*(\d+))?\s*$""",
    RegexOption.IGNORE_CASE,
)

fun parseDiscTag(raw: String): DiscTag? {
    val tagInGroup = TAG_IN_GROUP.find(raw)
    if (tagInGroup != null) {
        val (number, total) = parseNumbers(tagInGroup) ?: return null
        val stripped = collapseWhitespace(raw.replaceRange(tagInGroup.range, " "))
        return DiscTag(number, total, stripped)
    }

    val trailing = TRAILING_TAG.find(raw)
    if (trailing != null) {
        val (number, total) = parseNumbers(trailing) ?: return null
        val stripped = collapseWhitespace(raw.removeRange(trailing.range))
        return DiscTag(number, total, stripped)
    }

    return null
}

private fun parseNumbers(match: MatchResult): Pair<Int, Int?>? {
    val number = match.groupValues[1].toIntOrNull() ?: return null
    val total = match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull()
    return number to total
}

private fun collapseWhitespace(s: String): String =
    s.replace(Regex("\\s+"), " ").trim()
