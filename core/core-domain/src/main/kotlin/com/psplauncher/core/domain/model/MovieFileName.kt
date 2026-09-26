package com.psplauncher.core.domain.model

object MovieFileName {
    private val JUNK = setOf(
        "1080p", "720p", "480p", "576p", "2160p", "4k", "uhd",
        "bluray", "brrip", "bdrip", "webrip", "web", "webdl", "web-dl", "hdrip", "hdts", "hdtv",
        "dvdrip", "remux", "repack", "proper", "extended", "unrated", "limited", "internal",
        "x264", "x265", "h264", "h265", "hevc", "avc", "xvid", "divx",
        "aac", "ac3", "dts", "ddp5", "dd5", "dd2", "atmos", "truehd", "flac", "mp3",
        "hdr", "hdr10", "dv", "sdr", "imax", "ma", "amzn", "nf", "hmax", "dsnp",
    )

    private val YEAR = Regex("""^(19|20)\d{2}$""")

    fun bareTitleOf(fileName: String): String = parse(fileName).first

    fun yearOf(fileName: String): Int? = parse(fileName).second

    fun titleOf(fileName: String): String {
        val (title, year) = parse(fileName)
        return if (year != null) "$title ($year)" else title
    }

    private fun parse(fileName: String): Pair<String, Int?> {
        val stem = fileName.substringBeforeLast('.', fileName).trim()
        if (stem.isEmpty()) return fileName to null

        val tokens = stem.replace('.', ' ').replace('_', ' ').split(' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return fileName to null

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
