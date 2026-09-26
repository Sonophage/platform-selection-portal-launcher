package com.psplauncher.core.data.repository

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaDurationFallbackTest {
    @Test
    fun `Xing VBR MPEG1 layer III duration is frames times samples over sample rate`() {
        val bytes = xingMp3(
            versionBits = 3, layerIndex = 2, sampleRateIdx = 0, channelMode = 0,
            sideInfoSize = 32, frames = 3,
        )

        assertEquals(78L, durationOf(bytes, "audio/mpeg"))
    }

    @Test
    fun `Xing VBR MPEG2 layer III uses its 576 samples per frame`() {
        val bytes = xingMp3(
            versionBits = 2, layerIndex = 2, sampleRateIdx = 0, channelMode = 0,
            sideInfoSize = 17, frames = 2,
        )

        assertEquals(52L, durationOf(bytes, "audio/mpeg"))
    }

    @Test
    fun `Xing header without a frame count cannot be timed`() {
        val bytes = xingMp3(
            versionBits = 3, layerIndex = 2, sampleRateIdx = 0, channelMode = 0,
            sideInfoSize = 32, frames = null,
        )
        assertNull(durationOf(bytes, "audio/mpeg"))
    }

    @Test
    fun `CBR MP3 duration is audio bytes over bitrate, ID3 tags excluded`() {
        val bytes = cbrMp3(bitrateKbps = 128, payloadBytes = 1000)

        assertEquals(62L, durationOf(bytes, "audio/mpeg"))
    }

    @Test
    fun `CBR MP3 excludes an ID3v1 footer from the byte math`() {
        val payload = 1000
        val bytes = cbrMp3(bitrateKbps = 128, payloadBytes = payload)
        val withFooter = bytes + "TAG".toByteArray() + ByteArray(125)

        assertEquals(62L, durationOf(withFooter, "audio/mpeg"))
    }

    @Test
    fun `PCM WAV duration is data bytes over byte rate`() {
        val bytes = pcmWav(byteRate = 88_200, dataBytes = 44_100)
        assertEquals(500L, durationOf(bytes, "audio/wav"))
    }

    @Test
    fun `WAV with unknown data size falls back to everything after the header`() {
        val bytes = pcmWav(byteRate = 88_200, dataBytes = 44_100, unknownDataSize = true)
        assertEquals(500L, durationOf(bytes, "audio/wav"))
    }

    @Test
    fun `non-PCM WAV is left to the extractor`() {
        val bytes = pcmWav(byteRate = 88_200, dataBytes = 1_000, audioFormat = 3)
        assertNull(durationOf(bytes, "audio/wav"))
    }

    @Test
    fun `garbage bytes cannot be timed`() {
        val bytes = "not a media file at all".toByteArray() + ByteArray(64)
        assertNull(durationOf(bytes, "audio/mpeg"))
        assertNull(durationOf(bytes, "audio/wav"))
    }

    @Test
    fun `a mime the parser does not know is left to the extractor`() {
        val bytes = xingMp3(versionBits = 3, layerIndex = 2, sampleRateIdx = 0, channelMode = 0, sideInfoSize = 32, frames = 3)
        assertNull(durationOf(bytes, "audio/ogg"))
        assertNull(durationOf(bytes, null))
    }

    private fun durationOf(bytes: ByteArray, mime: String?): Long? {
        val file = File.createTempFile("dur", ".bin")
        file.writeBytes(bytes)
        try {
            return MediaDurationFallback.durationMs(file, mime)
        } finally {
            file.delete()
        }
    }

    private fun xingMp3(
        versionBits: Int,
        layerIndex: Int,
        sampleRateIdx: Int,
        channelMode: Int,
        sideInfoSize: Int,
        frames: Int?,
    ): ByteArray {
        val out = ByteArrayOutputStream()

        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 0x23))
        out.write(ByteArray(35))

        val layerBits = intArrayOf(3, 2, 1)[layerIndex]
        val versionLayer = (versionBits shl 3) or (layerBits shl 1)
        out.write(0xFF)
        out.write(0xE0 or versionLayer)
        out.write(0x50 or (sampleRateIdx shl 2))
        out.write(channelMode shl 6)
        out.write(ByteArray(sideInfoSize))
        out.write("Xing".toByteArray())
        val flags = if (frames != null) 0x0F else 0x02
        out.write(byteArrayOf(0, 0, 0, flags.toByte()))
        if (frames != null) {
            out.write(byteArrayOf(0, 0, 0, frames.toByte()))
            out.write(ByteArray(8))
        }
        out.write(ByteArray(512))
        return out.toByteArray()
    }

    private fun cbrMp3(bitrateKbps: Int, payloadBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 0x23))
        out.write(ByteArray(35))

        val bitrateIndex = MPEG1_L3_BITRATES.indexOf(bitrateKbps)
        check(bitrateIndex > 0) { "bitrate $bitrateKbps is not in the MPEG1 L3 table" }
        out.write(0xFF)
        out.write(0xFB)
        out.write((bitrateIndex shl 4) or 0x00)
        out.write(0x00)
        out.write(ByteArray(payloadBytes))
        return out.toByteArray()
    }

    private fun pcmWav(byteRate: Int, dataBytes: Int, unknownDataSize: Boolean = false, audioFormat: Int = 1): ByteArray {
        val data = ByteArray(dataBytes)
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray())
        writeIntLe(out, 36 + dataBytes)
        out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray())
        writeIntLe(out, 16)
        writeShortLe(out, audioFormat)
        writeShortLe(out, 1)
        writeIntLe(out, 44_100)
        writeIntLe(out, byteRate)
        writeShortLe(out, 2)
        writeShortLe(out, 16)
        out.write("data".toByteArray())
        writeIntLe(out, if (unknownDataSize) 0xFFFFFFFF.toInt() else dataBytes)
        out.write(data)
        return out.toByteArray()
    }

    private fun writeIntLe(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun writeShortLe(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private companion object {
        val MPEG1_L3_BITRATES = listOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0)
    }
}
