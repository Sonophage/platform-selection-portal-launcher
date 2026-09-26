package com.psplauncher.themekit

import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

object PtfParser {
    private val MAGIC = byteArrayOf(0x00, 'P'.code.toByte(), 'T'.code.toByte(), 'F'.code.toByte())
    private const val NAME_OFFSET = 0x08
    private const val NAME_LENGTH = 16
    private const val FIRMWARE_OFFSET = 0xB8
    private const val FIRMWARE_LENGTH = 8
    private const val TABLE_OFFSET = 0x100
    private const val MAX_SLOTS = 16

    private const val DESCRIPTOR_SIZE = 12
    private const val WALLPAPER_SLOT_ID = 1

    enum class Kind { OFFICIAL_PTF, CXMB, NOT_PTF }

    enum class WallpaperStatus {
        DECODED,

        MISSING,

        UNSUPPORTED_COMPRESSION,

        CORRUPT,
    }

    data class Slot(val id: Int, val subtype: Int, val size: Int, val dataOffset: Int)

    data class PtfTheme(
        val name: String,
        val firmware: String,
        val slots: List<Slot>,

        val wallpaper: BmpImage?,
        val wallpaperStatus: WallpaperStatus,
    )

    fun detect(bytes: ByteArray): Kind {
        if (bytes.size < TABLE_OFFSET + 4 || !bytes.startsWith(MAGIC)) return Kind.NOT_PTF
        return if (bytes.containsAscii("/vsh/resource/")) Kind.CXMB else Kind.OFFICIAL_PTF
    }

    fun parse(bytes: ByteArray): PtfTheme? {
        if (detect(bytes) != Kind.OFFICIAL_PTF) return null

        val cursor = bytes.cursor()
        val name = cursor.asciiAt(NAME_OFFSET, NAME_LENGTH)
        val firmware = cursor.asciiAt(FIRMWARE_OFFSET, FIRMWARE_LENGTH)

        val slots = buildList {
            for (i in 0 until MAX_SLOTS) {
                val ptr = cursor.pointerAt(TABLE_OFFSET + i * 4, needs = DESCRIPTOR_SIZE) ?: break
                add(
                    Slot(
                        id = cursor.u16At(ptr) ?: break,
                        subtype = cursor.u16At(ptr + 2) ?: break,

                        size = cursor.u32At(ptr + 4)?.toIntOrNullExact() ?: break,
                        dataOffset = cursor.u32At(ptr + 8)?.toIntOrNullExact() ?: break,
                    ),
                )
            }
        }

        val wallpaperSlot = slots.firstOrNull { it.id == WALLPAPER_SLOT_ID }
        val (wallpaper, status) = when {
            wallpaperSlot == null -> null to WallpaperStatus.MISSING
            else -> extractWallpaper(bytes, wallpaperSlot)
        }

        return PtfTheme(
            name = name,
            firmware = firmware,
            slots = slots,
            wallpaper = wallpaper,
            wallpaperStatus = status,
        )
    }

    private const val PAYLOAD_HEADER_SIZE = 32
    private const val RESOURCE_TYPE_WALLPAPER = 4
    private const val COMPRESSION_LZR = 1
    private const val COMPRESSION_ZLIB = 2

    private fun extractWallpaper(bytes: ByteArray, slot: Slot): Pair<BmpImage?, WallpaperStatus> {
        val start = slot.dataOffset
        val end = (slot.dataOffset.toLong() + slot.size).coerceAtMost(bytes.size.toLong()).toInt()
        if (start !in 0 until end) return null to WallpaperStatus.CORRUPT

        if (end - start >= PAYLOAD_HEADER_SIZE) {
            val cursor = bytes.cursor()
            val type = cursor.u16At(start + 4) ?: return null to WallpaperStatus.CORRUPT
            val method = cursor.u16At(start + 6) ?: return null to WallpaperStatus.CORRUPT
            val compressedSize = cursor.u32At(start + 8)?.toIntOrNullExact()
                ?: return null to WallpaperStatus.CORRUPT
            val uncompressedSize = cursor.u32At(start + 12)?.toIntOrNullExact()
                ?: return null to WallpaperStatus.CORRUPT
            val headerPlausible = type == RESOURCE_TYPE_WALLPAPER &&
                compressedSize in 1..(end - start - PAYLOAD_HEADER_SIZE) &&
                uncompressedSize in 1..MAX_INFLATED_BYTES
            if (headerPlausible) {
                when (method) {
                    COMPRESSION_LZR -> {
                        val decompressed = Lzr.decompress(
                            input = bytes,
                            offset = start + PAYLOAD_HEADER_SIZE,
                            length = compressedSize,
                            maxOutput = uncompressedSize,
                        )
                        val bmp = decompressed?.let(Bmp::decode)
                        return if (bmp != null) bmp to WallpaperStatus.DECODED
                        else null to WallpaperStatus.CORRUPT
                    }
                    COMPRESSION_ZLIB -> {
                        val inflated = inflate(bytes, start + PAYLOAD_HEADER_SIZE, compressedSize)
                        val bmp = inflated?.let(Bmp::decode)
                        if (bmp != null) return bmp to WallpaperStatus.DECODED
                    }
                    else -> return null to WallpaperStatus.UNSUPPORTED_COMPRESSION
                }
            }
        }

        val zlibStart = findZlibHeader(bytes, start, end)
            ?: return null to WallpaperStatus.CORRUPT
        val inflated = inflate(bytes, zlibStart, end - zlibStart)
            ?: return null to WallpaperStatus.CORRUPT
        val bmp = Bmp.decode(inflated) ?: return null to WallpaperStatus.CORRUPT
        return bmp to WallpaperStatus.DECODED
    }

    private fun findZlibHeader(bytes: ByteArray, from: Int, until: Int): Int? {
        for (i in from until until - 1) {
            if (bytes[i] == 0x78.toByte()) {
                val cmf = bytes[i].toInt() and 0xFF
                val flg = bytes[i + 1].toInt() and 0xFF
                if ((cmf * 256 + flg) % 31 == 0) return i
            }
        }
        return null
    }

    internal const val MAX_INFLATED_BYTES = 32 * 1024 * 1024

    internal fun inflate(bytes: ByteArray, offset: Int, length: Int): ByteArray? {
        val inflater = Inflater()
        inflater.setInput(bytes, offset, length)
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        try {
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n > 0) {
                    out.write(buffer, 0, n)
                    if (out.size() > MAX_INFLATED_BYTES) return null
                } else if (inflater.needsInput() || inflater.needsDictionary()) {
                    return null
                }
            }
        } catch (_: DataFormatException) {
            return null
        } finally {
            inflater.end()
        }
        return out.toByteArray()
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    private fun Long.toIntOrNullExact(): Int? = if (this in 0..Int.MAX_VALUE.toLong()) toInt() else null

    private fun ByteArray.containsAscii(needle: String): Boolean {
        val n = needle.toByteArray(Charsets.US_ASCII)
        outer@ for (i in 0..size - n.size) {
            for (j in n.indices) if (this[i + j] != n[j]) continue@outer
            return true
        }
        return false
    }
}
