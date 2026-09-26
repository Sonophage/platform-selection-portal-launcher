package com.psplauncher.feature.artwork.store

object PayloadCheck {
    fun accepts(kind: ArtworkKind, header: ByteArray): Boolean = extFor(kind, header) != null

    fun extFor(kind: ArtworkKind, header: ByteArray): String? = when (kind) {
        ArtworkKind.MANUAL -> "pdf".takeIf { header.startsWithAscii("%PDF") }
        ArtworkKind.VIDEO, ArtworkKind.ICON1 -> when {
            isFtyp(header) -> "mp4"
            isEbml(header) -> "webm"
            else           -> null
        }
        else -> ImageFormat.sniff(header)?.ext
    }

    private fun isFtyp(header: ByteArray): Boolean = header.size >= 8 &&
        header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
        header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()

    private fun isEbml(header: ByteArray): Boolean = header.size >= 4 &&
        header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
        header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()

    private fun ByteArray.startsWithAscii(text: String): Boolean =
        size >= text.length && text.withIndex().all { (i, c) -> this[i] == c.code.toByte() }
}
