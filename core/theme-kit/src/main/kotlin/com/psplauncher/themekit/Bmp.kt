package com.psplauncher.themekit

class BmpImage(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(argb.size == width * height) { "pixel buffer ${argb.size} != ${width}x$height" }
    }

    operator fun get(x: Int, y: Int): Int = argb[y * width + x]
}

object Bmp {
    private const val FILE_HEADER_SIZE = 14
    private const val MIN_INFO_HEADER_SIZE = 40

    private const val MAX_DIMENSION = 8192

    fun decode(bytes: ByteArray): BmpImage? {
        if (bytes.size < FILE_HEADER_SIZE + MIN_INFO_HEADER_SIZE) return null
        if (bytes[0] != 'B'.code.toByte() || bytes[1] != 'M'.code.toByte()) return null

        val pixelOffset = bytes.u32(10)
        val width = bytes.i32(18)
        val rawHeight = bytes.i32(22)
        val bpp = bytes.u16(28)
        val compression = bytes.u32(30)

        if (width <= 0 || rawHeight == 0) return null
        if (width > MAX_DIMENSION || rawHeight > MAX_DIMENSION || rawHeight < -MAX_DIMENSION) return null
        if (bpp != 24 || compression != 0) return null

        val bottomUp = rawHeight > 0
        val height = if (bottomUp) rawHeight else -rawHeight
        val rowStride = (width * 3 + 3) and 0x3.inv()
        if (pixelOffset < 0 || pixelOffset.toLong() + rowStride.toLong() * height > bytes.size) return null

        val argb = IntArray(width * height)
        for (row in 0 until height) {
            val srcRow = if (bottomUp) height - 1 - row else row
            var src = pixelOffset + srcRow * rowStride
            var dst = row * width
            repeat(width) {
                val b = bytes[src].toInt() and 0xFF
                val g = bytes[src + 1].toInt() and 0xFF
                val r = bytes[src + 2].toInt() and 0xFF
                argb[dst] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                src += 3
                dst++
            }
        }
        return BmpImage(width, height, argb)
    }

    private fun ByteArray.u16(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.u32(offset: Int): Int = i32(offset)

    private fun ByteArray.i32(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
}
