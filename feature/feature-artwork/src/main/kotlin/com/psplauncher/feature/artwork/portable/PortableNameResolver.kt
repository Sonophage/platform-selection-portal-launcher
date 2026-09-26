package com.psplauncher.feature.artwork.portable

object PortableNameResolver {
    const val MAX_LENGTH = 150

    private val ILLEGAL = Regex("[\\\\/:*?\"<>|]")

    private val WHITESPACE = Regex("\\s+")

    private val RESERVED_NAMES = buildSet {
        addAll(listOf("con", "prn", "aux", "nul"))
        (1..9).forEach { add("com$it"); add("lpt$it") }
    }

    fun fromRomFileName(romFileName: String): String =
        sanitize(ArtworkNaming.fileStem(romFileName))

    fun fromTitle(title: String): String = sanitize(title)

    fun sanitize(raw: String): String {
        val cleaned = raw
            .filterNot { it.isISOControl() }
            .replace(ILLEGAL, " ")
            .replace(WHITESPACE, " ")
            .trim()
            .trimStart('.')
            .trimEnd('.', ' ')
        val capped = cleaned.take(MAX_LENGTH).trimEnd('.', ' ')
        val safe = capped.ifBlank { "untitled" }
        return if (safe.lowercase() in RESERVED_NAMES) "$safe-x" else safe
    }
}
