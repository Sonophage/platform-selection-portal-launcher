package com.psplauncher.themekit

@JvmInline
value class ByteCursor(private val bytes: ByteArray) {
    val size: Int get() = bytes.size

    fun holds(offset: Int, length: Int): Boolean =
        offset >= 0 && length >= 0 && offset.toLong() + length <= bytes.size

    fun u8At(offset: Int): Int? =
        if (holds(offset, 1)) bytes[offset].toInt() and 0xFF else null

    fun u16At(offset: Int): Int? {
        if (!holds(offset, 2)) return null
        return (bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8)
    }

    fun u32At(offset: Int): Long? {
        if (!holds(offset, 4)) return null
        return (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }

    fun i32At(offset: Int): Int? = u32At(offset)?.toInt()

    fun pointerAt(offset: Int, needs: Int): Int? {
        val raw = u32At(offset) ?: return null
        if (raw <= 0 || raw + needs > bytes.size) return null
        return raw.toInt()
    }

    fun sliceAt(offset: Int, length: Int): ByteArray? {
        if (!holds(offset, length)) return null
        return bytes.copyOfRange(offset, offset + length)
    }

    fun asciiAt(offset: Int, maxLength: Int): String {
        if (offset < 0 || offset >= bytes.size) return ""
        val end = (offset.toLong() + maxLength).coerceAtMost(bytes.size.toLong()).toInt()
        val nul = (offset until end).firstOrNull { bytes[it] == 0.toByte() } ?: end
        return String(bytes, offset, nul - offset, Charsets.ISO_8859_1).trim()
    }

    fun raw(): ByteArray = bytes
}

internal fun ByteArray.cursor(): ByteCursor = ByteCursor(this)
