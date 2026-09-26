package com.psplauncher.feature.artwork.store

import java.util.Locale

object ArtworkFileNaming {
    val MULTI_ASSET_KINDS: Set<ArtworkKind> = setOf(ArtworkKind.SCREENSHOT, ArtworkKind.VIDEO)

    const val MAX_SORT_ORDER = 99

    private val ORDINAL_SUFFIX = Regex("_(\\d{2})$")

    fun supportsMultiple(kind: ArtworkKind): Boolean = kind in MULTI_ASSET_KINDS

    fun fixedName(kind: ArtworkKind, sortOrder: Int = 0): String {
        val bare = bareFixedName(kind)
        if (sortOrder <= 0) return bare
        return "${withOrdinal(bare.substringBeforeLast('.'), sortOrder)}.${bare.substringAfterLast('.')}"
    }

    private fun bareFixedName(kind: ArtworkKind): String = when (kind) {
        ArtworkKind.ICON       -> "icon.jpg"
        ArtworkKind.HERO       -> "hero.jpg"
        ArtworkKind.BACKGROUND -> "background.jpg"
        ArtworkKind.LOGO       -> "logo.png"
        ArtworkKind.MANUAL     -> "manual.pdf"
        ArtworkKind.VIDEO      -> "video.mp4"
        ArtworkKind.ICON1      -> "icon1.mp4"
        ArtworkKind.SCREENSHOT     -> "screenshot.jpg"
        ArtworkKind.TITLESCREEN    -> "titlescreen.jpg"
        ArtworkKind.PHYSICAL_MEDIA -> "physicalmedia.png"
        ArtworkKind.BOX_ART        -> "boxart.jpg"
        ArtworkKind.BOX_3D         -> "box3d.png"
    }

    fun baseName(kind: ArtworkKind, sortOrder: Int = 0): String =
        fixedName(kind, sortOrder).substringBeforeLast('.')

    fun versionedName(kind: ArtworkKind, ext: String, nowMillis: Long = System.currentTimeMillis()): String =
        "${kind.name.lowercase(Locale.US)}_$nowMillis.$ext"

    fun withOrdinal(stem: String, sortOrder: Int): String =
        if (sortOrder <= 0) stem else "${stem}_%02d".format(Locale.US, sortOrder.coerceAtMost(MAX_SORT_ORDER))

    fun nextOrdinal(slotNames: Collection<String>): Int {
        if (slotNames.isEmpty()) return 0
        val used = slotNames.mapTo(HashSet()) { ordinalOf(it) }
        val next = used.max() + 1
        if (next <= MAX_SORT_ORDER) return next
        return (0..MAX_SORT_ORDER).firstOrNull { it !in used } ?: MAX_SORT_ORDER
    }

    fun ordinalOf(stem: String): Int =
        ORDINAL_SUFFIX.find(stem)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..MAX_SORT_ORDER } ?: 0

    fun stripOrdinal(stem: String): String = ORDINAL_SUFFIX.replace(stem, "")

    fun sortOrderFromFileName(kind: ArtworkKind, fileName: String): Int? {
        val stem = fileName.substringBeforeLast('.')
        val base = baseName(kind)
        if (stem == base) return 0
        if (!supportsMultiple(kind)) return null
        val ordinal = ordinalOf(stem)
        return if (ordinal > 0 && stripOrdinal(stem) == base) ordinal else null
    }

    fun isPruneCandidate(kind: ArtworkKind, fileName: String, sortOrder: Int = 0): Boolean {
        if (fileName == fixedName(kind, sortOrder)) return true
        if (sortOrder > 0) return false

        if (!fileName.startsWith("${kind.name.lowercase(Locale.US)}_")) return false
        return sortOrderFromFileName(kind, fileName).let { it == null || it == 0 }
    }
}
