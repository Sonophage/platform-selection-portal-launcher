package com.psplauncher.core.ui.icons

import java.io.File

object GifFrameProbe {
    fun countFrames(file: File): Int = runCatching {
        countFrames(file.inputStream().use { it.readBytes() })
    }.getOrDefault(0)

    fun countFrames(bytes: ByteArray): Int {
        if (bytes.size < 13) return 0
        val sig = String(bytes, 0, 6, Charsets.US_ASCII)
        if (sig != "GIF87a" && sig != "GIF89a") return 0

        var i = 13
        if (bytes[10].toInt() and 0x80 != 0) {
            i += 3 * (1 shl ((bytes[10].toInt() and 0x07) + 1))
        }
        var frames = 0
        while (i < bytes.size) {
            when (bytes[i].toInt() and 0xFF) {
                0x21 -> {
                    i += 2
                    while (i < bytes.size) {
                        val len = bytes[i].toInt() and 0xFF
                        i += 1 + len
                        if (len == 0) break
                    }
                }
                0x2C -> {
                    frames++
                    if (i + 9 >= bytes.size) return frames
                    val packed = bytes[i + 9].toInt() and 0xFF
                    i += 10
                    if (packed and 0x80 != 0) i += 3 * (1 shl ((packed and 0x07) + 1))
                    if (i >= bytes.size) return frames
                    i += 1
                    while (i < bytes.size) {
                        val len = bytes[i].toInt() and 0xFF
                        i += 1 + len
                        if (len == 0) break
                    }
                }
                0x3B -> return frames
                else -> return frames
            }
        }
        return frames
    }
}
