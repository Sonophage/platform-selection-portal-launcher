package com.psplauncher.themekit

object Gim {
    private val MAGIC = "MIG.00.1PSP".toByteArray(Charsets.US_ASCII)

    private const val CHUNK_ROOT = 0x02
    private const val CHUNK_PICTURE = 0x03
    private const val CHUNK_IMAGE = 0x04
    private const val CHUNK_PALETTE = 0x05

    private const val FORMAT_RGBA5650 = 0
    private const val FORMAT_RGBA5551 = 1
    private const val FORMAT_RGBA4444 = 2
    private const val FORMAT_RGBA8888 = 3
    private const val FORMAT_INDEX4 = 4
    private const val FORMAT_INDEX8 = 5

    private const val MAX_DIMENSION = 8192
    private const val MAX_PALETTE_ENTRIES = 65536

    fun isGim(bytes: ByteArray): Boolean {
        if (bytes.size < MAGIC.size) return false
        for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) return false
        return true
    }

    fun decode(bytes: ByteArray): BmpImage? {
        if (!isGim(bytes) || bytes.size < 32) return null

        val cursor = bytes.cursor()
        var palette: IntArray? = null
        var offset = 16
        while (cursor.holds(offset, 16)) {
            val id = cursor.u16At(offset) ?: return null
            val size = cursor.i32At(offset + 4) ?: return null
            when (id) {
                CHUNK_ROOT, CHUNK_PICTURE -> {
                    offset += 16
                    continue
                }
                CHUNK_PALETTE -> palette = decodePalette(bytes, offset, size) ?: return null
                CHUNK_IMAGE -> return decodeImage(bytes, offset, size, palette)
            }

            if (size < 16) return null
            val next = offset.toLong() + size
            if (next > bytes.size) return null
            offset = next.toInt()
        }
        return null
    }

    private const val BLOCK_HEADER = 16

    private fun dataStart(bytes: ByteArray, chunk: Int): Int {
        val declared = bytes.cursor().u16At(chunk + BLOCK_HEADER + 28) ?: 64
        return chunk + BLOCK_HEADER + if (declared in 16..1024) declared else 64
    }

    private fun decodePalette(bytes: ByteArray, chunk: Int, size: Int): IntArray? {
        val cursor = bytes.cursor()
        if (!cursor.holds(chunk + BLOCK_HEADER, 32)) return null
        val format = cursor.u16At(chunk + BLOCK_HEADER + 4) ?: return null
        val entries = cursor.u16At(chunk + BLOCK_HEADER + 8) ?: return null
        if (format !in FORMAT_RGBA5650..FORMAT_RGBA8888 || entries !in 1..MAX_PALETTE_ENTRIES) return null
        val start = dataStart(bytes, chunk)
        val end = minOf(chunk + size, bytes.size)
        val bytesPer = if (format == FORMAT_RGBA8888) 4 else 2
        if (start + entries.toLong() * bytesPer > end) return null
        return IntArray(entries) { i -> directPixel(format, bytes, start + i * bytesPer) }
    }

    private fun decodeImage(bytes: ByteArray, chunk: Int, size: Int, palette: IntArray?): BmpImage? {
        val cursor = bytes.cursor()
        if (!cursor.holds(chunk + BLOCK_HEADER, 32)) return null
        val format = cursor.u16At(chunk + BLOCK_HEADER + 4) ?: return null
        val swizzled = cursor.u16At(chunk + BLOCK_HEADER + 6) == 1
        val width = cursor.u16At(chunk + BLOCK_HEADER + 8) ?: return null
        val height = cursor.u16At(chunk + BLOCK_HEADER + 10) ?: return null
        val bpp = cursor.u16At(chunk + BLOCK_HEADER + 12) ?: return null
        if (width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION) return null
        val expectedBpp = when (format) {
            FORMAT_RGBA8888 -> 32
            FORMAT_RGBA5650, FORMAT_RGBA5551, FORMAT_RGBA4444 -> 16
            FORMAT_INDEX4 -> 4
            FORMAT_INDEX8 -> 8
            else -> return null
        }
        if (bpp != expectedBpp) return null
        if (format >= FORMAT_INDEX4 && palette == null) return null

        val start = dataStart(bytes, chunk)
        val end = minOf(chunk + size, bytes.size)
        if (start >= end) return null

        val pitch = ((width * bpp + 7) / 8 + 15) / 16 * 16
        val storedHeight = if (swizzled) (height + 7) / 8 * 8 else height
        if (start + pitch.toLong() * storedHeight > end) return null
        var pixels = bytes.copyOfRange(start, start + pitch * storedHeight)
        if (swizzled) pixels = unswizzle(pixels, pitch)

        val argb = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * pitch
            for (x in 0 until width) {
                argb[y * width + x] = when (format) {
                    FORMAT_INDEX8 -> palette!!.getOrNull(pixels[row + x].toInt() and 0xFF) ?: return null
                    FORMAT_INDEX4 -> {
                        val pair = pixels[row + x / 2].toInt() and 0xFF
                        val index = if (x % 2 == 0) pair and 0x0F else pair ushr 4
                        palette!!.getOrNull(index) ?: return null
                    }
                    FORMAT_RGBA8888 -> directPixel(format, pixels, row + x * 4)
                    else -> directPixel(format, pixels, row + x * 2)
                }
            }
        }
        return BmpImage(width, height, argb)
    }

    private fun directPixel(format: Int, data: ByteArray, at: Int): Int {
        fun expand(v: Int, bits: Int): Int = v * 255 / ((1 shl bits) - 1)
        return when (format) {
            FORMAT_RGBA8888 -> {
                val r = data[at].toInt() and 0xFF
                val g = data[at + 1].toInt() and 0xFF
                val b = data[at + 2].toInt() and 0xFF
                val a = data[at + 3].toInt() and 0xFF
                (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            FORMAT_RGBA5650 -> {
                val v = data.u16(at)
                (0xFF shl 24) or (expand(v and 31, 5) shl 16) or
                    (expand((v ushr 5) and 63, 6) shl 8) or expand((v ushr 11) and 31, 5)
            }
            FORMAT_RGBA5551 -> {
                val v = data.u16(at)
                (if (v and 0x8000 != 0) 0xFF shl 24 else 0) or (expand(v and 31, 5) shl 16) or
                    (expand((v ushr 5) and 31, 5) shl 8) or expand((v ushr 10) and 31, 5)
            }
            else -> {
                val v = data.u16(at)
                (expand((v ushr 12) and 15, 4) shl 24) or (expand(v and 15, 4) shl 16) or
                    (expand((v ushr 4) and 15, 4) shl 8) or expand((v ushr 8) and 15, 4)
            }
        }
    }

    private fun unswizzle(data: ByteArray, pitch: Int): ByteArray {
        val height = data.size / pitch
        val out = ByteArray(data.size)
        val rowBlocks = pitch / 16
        for (y in 0 until height) {
            val blockRow = (y / 8) * rowBlocks
            val inBlockRow = (y % 8) * 16
            for (x in 0 until pitch) {
                out[y * pitch + x] = data[((x / 16) + blockRow) * 128 + inBlockRow + (x % 16)]
            }
        }
        return out
    }

    private fun ByteArray.u16(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.i32(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
}
