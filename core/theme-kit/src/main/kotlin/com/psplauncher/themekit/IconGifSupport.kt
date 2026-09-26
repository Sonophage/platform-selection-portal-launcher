package com.psplauncher.themekit

object IconGifSupport {
    const val MAX_DIMENSION = 512

    const val MAX_FRAMES = 120

    const val MAX_DURATION_MS = 10_000L

    const val MSG_TOO_LARGE_RESOLUTION = "Image is too large — 512px or smaller for a GIF"
    const val MSG_TOO_MANY_FRAMES = "GIF has too many frames — 120 or fewer"
    const val MSG_TOO_LONG = "GIF is too long — 10 seconds or less"
    const val MSG_TOO_LARGE_BYTES = "File is too large — under 8 MB"
    const val MSG_UNDECODABLE = "Couldn't read that image — try a different file"

    fun isGif(bytes: ByteArray): Boolean {
        if (bytes.size < 6) return false
        val sig = String(bytes, 0, 6, Charsets.US_ASCII)
        return sig == "GIF87a" || sig == "GIF89a"
    }

    fun logicalScreenSize(bytes: ByteArray): Pair<Int, Int>? {
        if (!isGif(bytes) || bytes.size < 10) return null
        val width = (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8)
        val height = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8)
        return width to height
    }

    fun countFrames(bytes: ByteArray): Int {
        if (!isGif(bytes) || bytes.size < 13) return 0
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

    fun durationMs(bytes: ByteArray): Long {
        if (!isGif(bytes) || bytes.size < 13) return 0
        var i = 13
        if (bytes[10].toInt() and 0x80 != 0) {
            i += 3 * (1 shl ((bytes[10].toInt() and 0x07) + 1))
        }
        var centiseconds = 0L
        while (i < bytes.size) {
            when (bytes[i].toInt() and 0xFF) {
                0x21 -> {
                    val label = bytes.getOrNull(i + 1)?.toInt()?.and(0xFF) ?: return centiseconds * 10
                    i += 2
                    if (label == 0xF9) {
                        val len = bytes.getOrNull(i)?.toInt()?.and(0xFF) ?: 0
                        if (len >= 4 && i + 4 < bytes.size) {
                            centiseconds += (bytes[i + 2].toInt() and 0xFF) or ((bytes[i + 3].toInt() and 0xFF) shl 8)
                        }
                    }
                    while (i < bytes.size) {
                        val len = bytes[i].toInt() and 0xFF
                        i += 1 + len
                        if (len == 0) break
                    }
                }
                0x2C -> {
                    if (i + 9 >= bytes.size) return centiseconds * 10
                    val packed = bytes[i + 9].toInt() and 0xFF
                    i += 10
                    if (packed and 0x80 != 0) i += 3 * (1 shl ((packed and 0x07) + 1))
                    if (i >= bytes.size) return centiseconds * 10
                    i += 1
                    while (i < bytes.size) {
                        val len = bytes[i].toInt() and 0xFF
                        i += 1 + len
                        if (len == 0) break
                    }
                }
                0x3B -> return centiseconds * 10
                else -> return centiseconds * 10
            }
        }
        return centiseconds * 10
    }

    fun validateAnimated(width: Int, height: Int, frameCount: Int, durationMs: Long): String? = when {
        width <= 0 || height <= 0 -> MSG_UNDECODABLE
        width > MAX_DIMENSION || height > MAX_DIMENSION -> MSG_TOO_LARGE_RESOLUTION
        frameCount > MAX_FRAMES -> MSG_TOO_MANY_FRAMES
        durationMs > MAX_DURATION_MS -> MSG_TOO_LONG
        else -> null
    }
}
