package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.common.format.formatByteSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── What sits under a library row's title ─────────────────────────────────────
//
// One file for all four libraries, because the point of these is that they agree. Each was built
// where its rows were built, and they had drifted into showing whatever that screen's author had
// to hand: a music track named its artist and nothing else, a video its running time and nothing
// else, while books and photos already said everything they knew. The same file of scanned
// metadata told you three different amounts depending on which category you were in.
//
// Pure functions, top level, taking the fields rather than the entities: the ViewModel needs a
// database and a Compose runtime to exist, and none of this needs either.
//
// The separator is the same everywhere on purpose. Books used one space around the dot and photos
// used two, which is the kind of difference nobody can name but everybody sees.

/** The separator between facts on a row. Narrower than the game metadata line's, which is bigger text. */
private const val DOT = "  ·  "

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/**
 * "1 game", "3 games", "2 shelves". The app's one rule for a count and the thing it counts.
 *
 * It is lowercase, and that is the whole reason this exists. Every count in the app was written
 * inline at the row that needed it, and the Game and App rows came out Title Case while tracks,
 * videos, libraries, shelves, books, albums and photos came out lowercase. Two D-pad presses apart,
 * the same widget in the same slot said "3 Games" and then "0 libraries". Nobody can name that
 * difference and everybody sees it.
 *
 * [plural] is explicit rather than always `singular + "s"` because shelf does not pluralise that
 * way, and a rule with one silent exception is not a rule.
 */
internal fun countLabel(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count ${if (count == 1) singular else plural}"

private fun join(vararg parts: String?): String? =
    parts.filterNotNull().joinToString(DOT).takeIf { it.isNotBlank() }

/**
 * A music track: who made it, what it is off, how long it runs.
 *
 * Album is the one that was missing everywhere. It is scanned, stored and, until now, shown on no
 * screen in the app at all.
 */
internal fun musicRowSubtitle(artist: String?, album: String?, durationMs: Long?): String? =
    join(artist.clean(), album.clean(), durationMs?.let(::formatDuration).clean())

/**
 * A video: how long it runs, how big the picture is, how much disk it takes.
 *
 * The same three slots as [photoRowSubtitle], in the same order — a duration where a photo has a
 * date, then resolution, then size — so the two media columns read as one list with one rule
 * rather than two screens that happen to look alike.
 *
 * The scanner has read all three since it was written ([VideoScanner] fills width, height and
 * size on every probe); the row simply never asked for them and showed a running time alone.
 *
 * **Last watched is deliberately not here.** It used to be the second slot, labelled out loud
 * because beside a running time a bare "3 days ago" reads just as easily as when the file was
 * added. That label was right, and if the watched cue comes back it comes back labelled. It was
 * dropped because four facts is more than a row can carry, and the file facts are what make this
 * row agree with the photo row. Resume state still lives on the video's detail page.
 */
internal fun videoRowSubtitle(
    durationMs: Long?,
    resolution: String?,
    sizeBytes: Long?,
): String? = join(
    durationMs?.let(::formatDuration).clean(),
    resolution.clean(),
    sizeBytes?.takeIf { it > 0 }?.let(::formatByteSize),
)

/**
 * A book: who wrote it, and where it falls in its series.
 *
 * The series is shown whatever the sort mode, not only when sorting by series: a list sorted by
 * title is exactly where "book 3 of something" is the fact the reader is missing.
 */
internal fun bookRowSubtitle(author: String?, series: String?, seriesIndex: Double?): String? {
    val seriesLabel = series.clean()?.let { name ->
        // A whole number is written without its decimal: "Dune #2", not "Dune #2.0". A .5 keeps
        // it, because that IS the information (a novella between two books).
        val index = seriesIndex?.let { i ->
            if (i == Math.floor(i)) "#${i.toInt()}" else "#$i"
        }
        listOfNotNull(name, index).joinToString(" ")
    }
    return join(author.clean(), seriesLabel)
}

/** A photo: when it was taken, how big it is on screen, how big it is on disk. */
internal fun photoRowSubtitle(dateMs: Long?, resolution: String?, sizeBytes: Long?): String? = join(
    dateMs?.takeIf { it > 0 }?.let(::formatDate),
    resolution.clean(),
    sizeBytes?.takeIf { it > 0 }?.let(::formatByteSize),
)

// ── Shared formatters ─────────────────────────────────────────────────────────

/** "3:42" under an hour, "1:42:07" over it. Empty for a duration that was never read. */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun formatDate(ms: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))

/**
 * "Today", "Yesterday", "9 days ago", then the date.
 *
 * It rolls over to a date at a month rather than counting forever. A library keeps things for
 * years, and "412 days ago" is a number you have to do arithmetic on to understand, which is the
 * opposite of what a relative date is for.
 *
 * [now] is a parameter so this can be tested without the test depending on what day it is run.
 */
internal fun relativeDate(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val days = ((now - epochMillis) / 86_400_000L)
    return when {
        // A clock that has gone backwards (a restored backup, a device whose time was wrong) must
        // not produce "-3 days ago". The date is always true.
        days < 0L -> formatDate(epochMillis)
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days <= 30L -> "$days days ago"
        else -> formatDate(epochMillis)
    }
}

/**
 * "812 KB", "3.2 MB". Moved here from the Artwork Studio, which was the only place it lived.
 *
 * Here because a photo row needs it and a ViewModel must not reach into a screen for a formatter.
 * There are two more byte formatters in the app, in VideoDetailScreen and PhotoViewerScreen, each
 * with its own idea of the unit to round to; they are untouched and worth collapsing into this one
 * the next time either is edited.
 */
