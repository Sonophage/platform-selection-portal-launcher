package com.psplauncher.studio

import com.psplauncher.studio.io.ImageCodecs
import com.psplauncher.studio.io.SafeIo
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SafeIoTest {
    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("safeio", ".bin").apply { writeBytes(bytes); deleteOnExit() }

    @Test
    fun `reads files within the cap intact`() {
        val data = ByteArray(1024) { it.toByte() }
        assertContentEquals(data, SafeIo.readBytesCapped(tempFile(data), cap = 1024))
    }

    @Test
    fun `rejects files over the cap`() {
        assertNull(SafeIo.readBytesCapped(tempFile(ByteArray(2048)), cap = 1024))
    }

    @Test
    fun `stream cap bails mid-read`() {
        with(SafeIo) {
            assertNull(ByteArray(200_000).inputStream().readCapped(cap = 100_000))
            assertNotNull(ByteArray(100_000).inputStream().readCapped(cap = 100_000))
        }
    }

    @Test
    fun `image decode rejects crafted huge-dimension headers before allocation`() {
        val png = buildList<Byte> {
            addAll(listOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            addAll(listOf(0x00, 0x00, 0x00, 0x0D))
            addAll("IHDR".map { it.code.toByte() })
            addAll(listOf(0x00, 0x01, 0x86.toByte(), 0xA0.toByte()))
            addAll(listOf(0x00, 0x01, 0x86.toByte(), 0xA0.toByte()))
            addAll(listOf(0x08, 0x06, 0x00, 0x00, 0x00))
            addAll(listOf(0x00, 0x00, 0x00, 0x00))
        }.toByteArray()
        assertNull(ImageCodecs.decodeImage(png))
    }

    @Test
    fun `image decode accepts normal images`() {
        val img = java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val png = ImageCodecs.toPngBytes(img)
        assertNotNull(ImageCodecs.decodeImage(png))
    }
}
