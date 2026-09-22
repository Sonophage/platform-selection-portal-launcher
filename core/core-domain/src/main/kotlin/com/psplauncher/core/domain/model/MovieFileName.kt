package com.psplauncher.core.domain.model

/**
 * Turns a scene-release filename into something readable.
 *
 * A video library is files off a disk, and those files are named for the people who made the
 * release, not for the person watching: "Dune.2021.1080p.BluRay.1600MB.DD2.0.x264-GalaxyRG.mkv".
 * The XMB then shows that, in full, as the title of the film.
 *
 * Derived at READ time and never written. Video.title is the user's own name for a file and is
 * deliberately preserved across rescans; putting a guess in that column would make the guess look
 * like their edit, and they could never get back to the filename. So this only ever fills in for
 * a title that is absent — see Video.displayTitle.
 */
object MovieFileName {

    /** Tokens that mean "the title has ended and the release metadata has begun". */
    private val JUNK = setOf(
        "1080p", "720p", "480p", "576p", "2160p", "4k", "uhd",
        "bluray", "brrip", "bdrip", "webrip", "web", "webdl", "web-dl", "hdrip", "hdts", "hdtv",
        "dvdrip", "remux", "repack", "proper", "extended", "unrated", "limited", "internal",
        "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx",
        "aac", "ac3", "dts", "ddp5", "dd5", "dd2", "atmos", "truehd", "flac", "mp3",
        "hdr", "hdr10", "dv", "sdr", "imax", "ma", "amzn", "nf", "hmax", "dsnp",
    )

    private val YEAR = Regex("""^(19|20)\d{2}$""")

    /** The title alone, without the year — what a search wants as its query. */
    fun bareTitleOf(fileName: String): String = parse(fileName).first

    /** The release year, or null when the name carries none. */
    fun yearOf(fileName: String): Int? = parse(fileName).second

    /**
     * The readable title for [fileName], as "Title (Year)" when a year is found.
     *
     * Returns the name unchanged when there is nothing recognisable to cut, which is the safe
     * direction: a file that is not a scene release should come through as itself rather than be
     * truncated by a guess.
     */
    fun titleOf(fileName: String): String {
        val (title, year) = parse(fileName)
        return if (year != null) "$title ($year)" else title
    }

    /**
     * Title and year in one pass, so the three public entry points cannot disagree about where
     * the title ends — which they would the moment one of them was tuned and the others were not.
     */
    private fun parse(fileName: String): Pair<String, Int?> {
        val stem = fileName.substringBeforeLast('.', fileName).trim()
        if (stem.isEmpty()) return fileName to null

        // Dots and underscores are separators in these names; a release group trails after a dash
        // at the very end and goes with the junk it sits in.
        val tokens = stem.replace('.', ' ').replace('_', ' ').split(' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return fileName to null

        // The LAST year, not the first. "Blade Runner 2049 2017 1080p" has two, and the first one
        // is part of the title — taking the first would call the film "Blade Runner" and date it
        // 2049. Films named for a year ("2012") break the same way.
        val yearIndex = tokens.indexOfLast { YEAR.matches(it) }
        val junkIndex = tokens.indexOfFirst { it.lowercase().trimStart('[', '(').trimEnd(']', ')') in JUNK }

        val cut = when {
            yearIndex > 0 -> yearIndex
            junkIndex > 0 -> junkIndex
            else -> tokens.size
        }

        val title = tokens.take(cut).joinToString(" ").trim(' ', '-', '–', ':')
        if (title.isEmpty()) return fileName to null

        val year = tokens.getOrNull(yearIndex)?.takeIf { yearIndex > 0 }?.toIntOrNull()
        return title to year
    }
}
