package com.psplauncher.feature.library.scanner

import java.nio.ByteBuffer
import java.nio.ByteOrder

object ParamSfo {
    private const val MAGIC = 0x46535000
    private const val FMT_UTF8 = 0x0204
    private const val FMT_UTF8_SPECIAL = 0x0004

    fun parseStrings(bytes: ByteArray): Map<String, String> {
        if (bytes.size < 20) return emptyMap()
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (b.getInt(0) != MAGIC) return emptyMap()

        val keyTableStart = b.getInt(8)
        val dataTableStart = b.getInt(12)
        val indexCount = b.getInt(16)
        if (indexCount !in 0..4096) return emptyMap()

        val out = LinkedHashMap<String, String>()
        for (i in 0 until indexCount) {
            val e = 20 + i * 16
            if (e + 16 > bytes.size) break
            val keyOffset = b.getShort(e).toInt() and 0xFFFF
            val dataFmt = b.getShort(e + 2).toInt() and 0xFFFF
            val dataLen = b.getInt(e + 4)
            val dataOffset = b.getInt(e + 12)
            if (dataFmt != FMT_UTF8 && dataFmt != FMT_UTF8_SPECIAL) continue

            val key = readCString(bytes, keyTableStart + keyOffset) ?: continue
            val ds = dataTableStart + dataOffset
            if (ds < 0 || dataLen < 0 || ds + dataLen > bytes.size) continue

            val value = String(bytes, ds, dataLen, Charsets.UTF_8).substringBefore(0.toChar()).trim()
            if (value.isNotEmpty()) out[key] = value
        }
        return out
    }

    fun title(bytes: ByteArray): String? = parseStrings(bytes)["TITLE"]

    private fun readCString(bytes: ByteArray, start: Int): String? {
        if (start < 0 || start >= bytes.size) return null
        var end = start
        while (end < bytes.size && bytes[end] != 0.toByte()) end++
        return String(bytes, start, end - start, Charsets.UTF_8)
    }
}
