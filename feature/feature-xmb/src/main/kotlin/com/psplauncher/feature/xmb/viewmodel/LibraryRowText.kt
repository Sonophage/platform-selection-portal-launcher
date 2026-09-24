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
    val days = daysSince(epochMillis, now)
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
 * Elapsed 24-hour blocks between the two instants.
 *
 * ONE definition, because [relativeDate] and [relativeDateTime] both branch on it and two copies
 * of the arithmetic would drift the moment either threshold was retuned — the second function
 * would then attach a clock time to a day the first had stopped calling "Today".
 *
 * Elapsed blocks, NOT calendar days: something played at 11pm reads as "Today" until 11pm the
 * following night. That is pre-existing and is left alone here, but [relativeDateTime] makes it
 * more visible than it was, since it now prints a clock time beside the word.
 */
private fun daysSince(epochMillis: Long, now: Long): Long = (now - epochMillis) / 86_400_000L

/**
 * [relativeDate], plus the clock time when it happened today.
 *
 * "Today" on its own is not much of an answer for a library you touch several times a day, and the
 * redesign's row meta reads "Game Boy Advance · Today, 1:49 PM". Past today the time stops being
 * the useful part — what you want from something played three weeks ago is "three weeks ago" — so
 * only the same-day case carries it.
 */
internal fun relativeDateTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val day = relativeDate(epochMillis, now)
    if (daysSince(epochMillis, now) != 0L) return day
    return "$day, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
}

/**
 * A game row's meta line: the system, then when you last played it.
 *
 * "Game Boy Advance · Today, 1:49 PM", and the redesign's note on it was "This is what is missing
 * from what we have now. if it hasnt been played it defaults to just the system and publisher".
 *
 * Three cases, in order: a real play record wins; a game never started falls back to its
 * publisher; and one with neither is left as the bare system name rather than trailing a separator
 * with nothing behind it.
 *
 * Here rather than inside the ViewModel that calls it, so the ORDER can be tested. It is the whole
 * rule, it has three branches, and two of them only appear for library entries that are hard to
 * arrange by hand on a device — a game with a publisher and no play record, and one with neither.
 *
 * [lastPlayedAt] of 0 counts as never played. It is a real instant (1970) that the column's
 * default writes, and sent to a date formatter it reads as fifty years ago rather than as absent.
 */
internal fun gameMetaLine(
    platform: String,
    lastPlayedAt: Long?,
    publisher: String?,
    now: Long = System.currentTimeMillis(),
): String {
    val tail = lastPlayedAt?.takeIf { it > 0L }?.let { relativeDateTime(it, now) }
        ?: publisher?.takeIf { it.isNotBlank() }
    return if (tail != null) "$platform  ·  $tail" else platform
}

/**
 * "812 KB", "3.2 MB". Moved here from the Artwork Studio, which was the only place it lived.
 *
 * Here because a photo row needs it and a ViewModel must not reach into a screen for a formatter.
 * There are two more byte formatters in the app, in VideoDetailScreen and PhotoViewerScreen, each
 * with its own idea of the unit to round to; they are untouched and worth collapsing into this one
 * the next time either is edited.
 */

/**
 * How far through a video you are, 0..1, or null when there is nothing to say.
 *
 * Null rather than 0 for "not started": the bar and the words both key off null to mean "say
 * nothing", and a zero would draw an empty bar on every video that has never been opened.
 * A missing or nonsense duration is the same case — a fraction of an unknown length is not a
 * fraction, and a resume point past the end is a stale stamp, not 110% watched.
 */
internal fun videoProgressFraction(resumePositionMs: Long, durationMs: Long?): Float? {
    if (resumePositionMs <= 0L) return null
    val duration = durationMs?.takeIf { it > 0L } ?: return null
    return (resumePositionMs.toFloat() / duration).takeIf { it < 1f }
}

/**
 * "34 min left", beside the bar.
 *
 * Time REMAINING, not elapsed and not a percentage: what the next press costs you is the
 * question a resume point answers, and "62%" makes you do the arithmetic to get there.
 */
internal fun videoProgressLabel(resumePositionMs: Long, durationMs: Long?): String? {
    val duration = durationMs?.takeIf { it > 0L } ?: return null
    if (videoProgressFraction(resumePositionMs, durationMs) == null) return null
    val remainingMin = ((duration - resumePositionMs) / 60_000L).toInt()
    return when {
        remainingMin <= 0 -> "Almost finished"
        remainingMin < 60 -> "$remainingMin min left"
        else -> {
            val h = remainingMin / 60
            val m = remainingMin % 60
            if (m == 0) "$h hr left" else "$h hr $m min left"
        }
    }
}
