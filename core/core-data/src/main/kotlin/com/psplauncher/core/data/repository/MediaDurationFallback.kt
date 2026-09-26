package com.psplauncher.core.data.repository

import java.io.File
import java.io.RandomAccessFile

object MediaDurationFallback {
    private const val HEAD_BYTES = 64 * 1024

    private const val ID3V1_FOOTER = 128

    fun durationMs(file: File, mime: String?): Long? {
        val total = file.length()
        if (total <= 0) return null
        val head = runCatching {
            file.inputStream().use { input ->
                val want = minOf(HEAD_BYTES, total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                val buf = ByteArray(want)
                val read = input.read(buf)
                if (read <= 0) ByteArray(0) else buf.copyOf(read)
            }
        }.getOrNull() ?: return null
        val tail = runCatching {
            if (total >= ID3V1_FOOTER) {
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(total - ID3V1_FOOTER)
                    ByteArray(ID3V1_FOOTER).also { raf.readFully(it) }
                }
            } else null
        }.getOrNull()
        return when (mime?.lowercase()) {
            "audio/wav", "audio/x-wav" -> wavDurationMs(head, total)
            "audio/mpeg" -> mp3DurationMs(head, total, tail)
            else -> null
        }
    }

    private val SAMPLE_RATES = arrayOf(
        intArrayOf(11025, 12000, 8000),
        IntArray(3),
        intArrayOf(22050, 24000, 16000),
        intArrayOf(44100, 48000, 32000),
    )

    private val BITRATES = arrayOf(

        arrayOf(
            intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 0),
            intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0),
            intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0),
        ),
        arrayOf(IntArray(16), IntArray(16), IntArray(16)),

        arrayOf(
            intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256, 0),
            intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0),
            intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0),
        ),

        arrayOf(
            intArrayOf(0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448, 0),
            intArrayOf(0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384, 0),
            intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0),
        ),
    )

    private fun mp3DurationMs(head: ByteArray, total: Long, tail: ByteArray?): Long? {
        var pos = 0
        if (head.size >= 10 && head.markerAt(0, "ID3")) {
            val tagSize = ((head[6].toInt() and 0x7F) shl 21) or
                ((head[7].toInt() and 0x7F) shl 14) or
                ((head[8].toInt() and 0x7F) shl 7) or
                (head[9].toInt() and 0x7F)
            pos = 10 + tagSize
        }

        while (pos + 4 <= head.size && !(head[pos] == 0xFF.toByte() && (head[pos + 1].toInt() and 0xE0) == 0xE0)) {
            pos++
        }
        if (pos + 4 > head.size) return null

        val b1 = head[pos + 1].toInt() and 0xFF
        val b2 = head[pos + 2].toInt() and 0xFF
        val b3 = head[pos + 3].toInt() and 0xFF
        val version = (b1 shr 3) and 3
        if (version == 1) return null
        val layerIndex = when ((b1 shr 1) and 3) {
            3 -> 0
            2 -> 1
            1 -> 2
            else -> return null
        }
        val sampleRateIndex = (b2 shr 2) and 3
        if (sampleRateIndex >= 3) return null
        val sampleRate = SAMPLE_RATES[version][sampleRateIndex]

        val channelMode = (b3 shr 6) and 3
        val sideInfoSize = if (version == 3) {
            if (channelMode == 3) 17 else 32
        } else {
            if (channelMode == 3) 9 else 17
        }
        val marker = pos + 4 + sideInfoSize
        if (marker + 12 <= head.size && (head.markerAt(marker, "Xing") || head.markerAt(marker, "Info"))) {
            val flags = head.be32(marker + 4)
            if (flags and 1L == 0L) return null
            val frames = head.be32(marker + 8)
            if (frames <= 0) return null
            val samplesPerFrame = when (layerIndex) {
                0 -> 384L
                1 -> 1152L
                else -> if (version == 3) 1152L else 576L
            }
            return frames * samplesPerFrame * 1000L / sampleRate
        }

        val bitrateIndex = (b2 shr 4) and 0xF
        val kbps = BITRATES[version][layerIndex][bitrateIndex]
        if (kbps == 0) return null
        var audioBytes = total - pos
        if (tail != null && tail.markerAt(0, "TAG")) audioBytes -= ID3V1_FOOTER
        if (audioBytes <= 0) return null
        return audioBytes * 8 * 1000 / (kbps.toLong() * 1000)
    }

    private fun wavDurationMs(head: ByteArray, total: Long): Long? {
        if (head.size < 12 || !head.markerAt(0, "RIFF") || !head.markerAt(8, "WAVE")) return null
        var pos = 12
        var byteRate = 0L
        var dataSize = -1L
        while (pos + 8 <= head.size) {
            val size = head.le32(pos + 4)
            when {
                head.markerAt(pos, "fmt ") -> {
                    if (head.le16(pos + 8) != 1) return null
                    byteRate = head.le32(pos + 16)
                }
                head.markerAt(pos, "data") -> {
                    dataSize = if (size == 0xFFFFFFFFL) total - pos - 8 else size
                }
            }

            val chunkTotal = 8L + size + (size and 1L)
            if (chunkTotal > head.size - pos) break
            pos += chunkTotal.toInt()
        }
        if (dataSize <= 0 || byteRate <= 0) return null
        return dataSize * 1000 / byteRate
    }

    private fun ByteArray.markerAt(offset: Int, marker: String): Boolean =
        offset + marker.length <= size && String(this, offset, marker.length, Charsets.ISO_8859_1) == marker

    private fun ByteArray.be32(offset: Int): Long =
        ((this[offset].toInt() and 0xFF).toLong() shl 24) or
            ((this[offset + 1].toInt() and 0xFF).toLong() shl 16) or
            ((this[offset + 2].toInt() and 0xFF).toLong() shl 8) or
            (this[offset + 3].toInt() and 0xFF).toLong()

    private fun ByteArray.le32(offset: Int): Long =
        (this[offset].toInt() and 0xFF).toLong() or
            ((this[offset + 1].toInt() and 0xFF).toLong() shl 8) or
            ((this[offset + 2].toInt() and 0xFF).toLong() shl 16) or
            ((this[offset + 3].toInt() and 0xFF).toLong() shl 24)

    private fun ByteArray.le16(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
}
