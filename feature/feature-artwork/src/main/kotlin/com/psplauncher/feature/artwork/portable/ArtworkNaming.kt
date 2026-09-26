package com.psplauncher.feature.artwork.portable

import java.text.Normalizer
import java.util.Locale

object ArtworkNaming {
    const val NORMALIZATION_VERSION = 1

    const val MAX_SLUG_LENGTH = 100

    private val RESERVED_NAMES = buildSet {
        addAll(listOf("con", "prn", "aux", "nul"))
        (1..9).forEach { add("com$it"); add("lpt$it") }
    }

    private val APOSTROPHES = Regex("[’‘´`]")

    private val TAG_GROUPS = Regex("""\(([^)]*)\)|\[([^\]]*)]""")

    private val DISC_TAG = Regex("""\((?:disc|disk|cd)\s*(\d+)[^)]*\)""", RegexOption.IGNORE_CASE)

    private val WHITESPACE = Regex("""\s+""")

    fun fileStem(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    fun normalizeForMatch(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT)
            .replace(APOSTROPHES, "'")
            .replace(WHITESPACE, " ")
            .trim()

    fun simplifyTitle(name: String): String =
        normalizeForMatch(name)
            .replace(TAG_GROUPS, " ")
            .replace("&", " and ")
            .replace(Regex("""[:\-_.,!?']"""), " ")
            .replace(WHITESPACE, " ")
            .trim()

    fun slug(stem: String): String {
        val normalized = normalizeForMatch(stem)
        val disc = DISC_TAG.find(normalized)?.groupValues?.get(1)
        val base = normalized
            .replace(TAG_GROUPS, " ")
            .replace(Regex("""[^a-z0-9._-]+"""), "-")
            .replace(Regex("-{2,}"), "-")
            .trim('-', '.', '_')
        val withDisc = if (disc != null) "$base-disc$disc" else base
        val capped = withDisc.take(MAX_SLUG_LENGTH).trim('-', '.', '_')
        val safe = capped.ifBlank { "untitled" }
        return if (safe in RESERVED_NAMES) "$safe-x" else safe
    }
}
