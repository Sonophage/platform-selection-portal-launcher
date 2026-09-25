package com.psplauncher.feature.xmb.viewmodel

// ── Letter jump: the A–Z scrubber for a long list ─────────────────────────────
//
// Hold a shoulder on a list of any length worth scrolling and an A–Z rail comes up; move along it
// and the list follows under the cursor; let go and you are there. Touch drags the same rail.
//
// The whole of it is here, as pure functions over the list that is already on screen, for the
// reason AppPickerLogic gives: the rules are the part worth testing and they need no ViewModel.
//
// THE RAIL IS DERIVED, NOT DECLARED, and that is the one decision to keep. It would have been
// easier to offer a fixed A–Z and a list of "sort modes letter jump is allowed on" — and that is
// a second list, which stops agreeing with the sorts the first time one is added. Instead this
// reads the list it was handed: the letters it offers are the letters that are actually there,
// and if the initials do not run in order then the list is not alphabetical and it offers nothing
// at all. A scrubber on a date-sorted list is not a smaller feature, it is a wrong one — the rung
// says M and the row it lands on does not.
//
// The initial is taken from [XMBItem.title], which is the same string the sorts order by
// (`sortedBy { it.displayTitle.lowercase() }` — one definition, read twice, never copied).

/** One rung of the rail: the letter, and the first row in the list that starts with it. */
data class LetterAnchor(val letter: Char, val index: Int)

/**
 * The scrubber while it is up.
 *
 * [cursor] indexes [anchors] and is always valid — [anchors] is never empty for a state that
 * exists, because a rail with nothing on it is not raised in the first place.
 */
data class LetterJumpState(
    val anchors: List<LetterAnchor>,
    val cursor: Int = 0,
    /** Where the list cursor sat when the rail came up, so BACK can put it back. */
    val returnIndex: Int = 0,
) {
    val letter: Char get() = anchors[cursor].letter
    val targetIndex: Int get() = anchors[cursor].index
}

/**
 * Shorter than this and the rail is not worth the screen: you can reach anything with the stick
 * faster than you can raise it, read it and aim.
 */
internal const val LETTER_JUMP_MIN_ITEMS = 25

/** Two rungs is a toggle, not a scrubber. */
internal const val LETTER_JUMP_MIN_LETTERS = 3

/**
 * The letter a title files under: itself uppercased for anything alphabetic, '#' for everything
 * else — digits, punctuation, an empty title.
 *
 * `isLetter` rather than an A–Z range on purpose. An accented title sorts after `z` when the list
 * is ordered by `lowercase()`, and `uppercaseChar()` keeps it after `Z` here, so the two orders
 * still agree and the rail simply grows a rung. Folding it into '#' instead would file it at the
 * front while the row sits at the back, which is exactly the disagreement [letterAnchors] refuses
 * to publish.
 */
internal fun initialOf(title: String): Char {
    val c = title.trimStart().firstOrNull() ?: return '#'
    return if (c.isLetter()) c.uppercaseChar() else '#'
}

/**
 * The rail for [items], or null when this list should not have one.
 *
 * Null for three separate reasons, and they are not interchangeable:
 *  - too few rows to be worth scrubbing ([LETTER_JUMP_MIN_ITEMS]);
 *  - the initials do not run in non-decreasing order, so the list is not alphabetical and a rail
 *    over it would point at the wrong rows;
 *  - fewer than [LETTER_JUMP_MIN_LETTERS] distinct letters, so the rail would be a stub.
 *
 * '#' leads when it is present: a non-letter initial sorts before `a` in the list for the same
 * reason it sorts before 'A' here.
 */
internal fun letterAnchors(items: List<XMBItem>): List<LetterAnchor>? {
    if (items.size < LETTER_JUMP_MIN_ITEMS) return null

    val anchors = ArrayList<LetterAnchor>()
    var previous: Char? = null
    for ((index, item) in items.withIndex()) {
        val letter = initialOf(item.title)
        if (previous != null && letter < previous) return null   // not alphabetical — say nothing
        if (letter != previous) anchors.add(LetterAnchor(letter, index))
        previous = letter
    }
    return if (anchors.size < LETTER_JUMP_MIN_LETTERS) null else anchors
}

/**
 * Raise the rail over [items] with the cursor on the rung the list is already sitting in, so it
 * opens where the eye already is rather than snapping to 'A'.
 */
internal fun letterJumpFor(items: List<XMBItem>, currentIndex: Int): LetterJumpState? {
    val anchors = letterAnchors(items) ?: return null
    // The last rung whose anchor is at or before the cursor — indexOfLast, not indexOfFirst,
    // because every rung before the current one also satisfies `index <= currentIndex`.
    val cursor = anchors.indexOfLast { it.index <= currentIndex }.coerceAtLeast(0)
    return LetterJumpState(anchors = anchors, cursor = cursor, returnIndex = currentIndex)
}

/**
 * Step the rail. Never wraps and never leaves it: the ends are walls, as they are everywhere else
 * in this app's lists.
 */
internal fun LetterJumpState.move(delta: Int): LetterJumpState {
    val next = (cursor + delta).coerceIn(0, anchors.lastIndex)
    return if (next == cursor) this else copy(cursor = next)
}

/** The rung nearest a 0f..1f position along the rail — what a finger dragging it lands on. */
internal fun LetterJumpState.atFraction(fraction: Float): LetterJumpState {
    if (anchors.size <= 1) return this
    val next = (fraction.coerceIn(0f, 1f) * (anchors.size - 1)).toInt().coerceIn(0, anchors.lastIndex)
    return if (next == cursor) this else copy(cursor = next)
}
