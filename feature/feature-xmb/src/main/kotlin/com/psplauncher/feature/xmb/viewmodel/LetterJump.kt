package com.psplauncher.feature.xmb.viewmodel

data class LetterAnchor(val letter: Char, val index: Int)

data class LetterJumpState(
    val anchors: List<LetterAnchor>,
    val cursor: Int = 0,

    val returnIndex: Int = 0,
) {
    val letter: Char get() = anchors[cursor].letter
    val targetIndex: Int get() = anchors[cursor].index
}

internal const val LETTER_JUMP_MIN_ITEMS = 25

internal const val LETTER_JUMP_MIN_LETTERS = 3

internal fun initialOf(title: String): Char {
    val c = title.trimStart().firstOrNull() ?: return '#'
    return if (c.isLetter()) c.uppercaseChar() else '#'
}

internal fun letterAnchors(items: List<XMBItem>): List<LetterAnchor>? {
    if (items.size < LETTER_JUMP_MIN_ITEMS) return null

    val anchors = ArrayList<LetterAnchor>()
    var previous: Char? = null
    for ((index, item) in items.withIndex()) {
        val letter = initialOf(item.title)
        if (previous != null && letter < previous) return null
        if (letter != previous) anchors.add(LetterAnchor(letter, index))
        previous = letter
    }
    return if (anchors.size < LETTER_JUMP_MIN_LETTERS) null else anchors
}

internal fun letterJumpFor(items: List<XMBItem>, currentIndex: Int): LetterJumpState? {
    val anchors = letterAnchors(items) ?: return null

    val cursor = anchors.indexOfLast { it.index <= currentIndex }.coerceAtLeast(0)
    return LetterJumpState(anchors = anchors, cursor = cursor, returnIndex = currentIndex)
}

internal fun LetterJumpState.move(delta: Int): LetterJumpState {
    val next = (cursor + delta).coerceIn(0, anchors.lastIndex)
    return if (next == cursor) this else copy(cursor = next)
}

internal fun LetterJumpState.atFraction(fraction: Float): LetterJumpState {
    if (anchors.size <= 1) return this
    val next = (fraction.coerceIn(0f, 1f) * (anchors.size - 1)).toInt().coerceIn(0, anchors.lastIndex)
    return if (next == cursor) this else copy(cursor = next)
}
