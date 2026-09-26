package com.psplauncher.studio

import java.io.ByteArrayOutputStream

object IconGifTestMedia {
    fun animatedGif(frames: Int = 3, delayCs: Int = 10, width: Int = 64, height: Int = 64): ByteArray {
        val out = ByteArrayOutputStream()

        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        out.write(byteArrayOf(width.toLo(), width.toHi(), height.toLo(), height.toHi(), 0x00, 0x00, 0x00))
        repeat(frames) {
            out.write(0x21); out.write(0xF9); out.write(0x04)
            out.write(0x00)
            out.write(byteArrayOf(delayCs.toLo(), delayCs.toHi()))
            out.write(0x00); out.write(0x00)

            out.write(0x2C)
            out.write(byteArrayOf(0, 0, 0, 0, width.toLo(), width.toHi(), height.toLo(), height.toHi(), 0x00))
            out.write(0x02)
            out.write(0x02)
            out.write(byteArrayOf(0x44, 0x01.toByte()))
            out.write(0x00)
        }
        out.write(0x3B)
        return out.toByteArray()
    }

    fun singleFrameGif(): ByteArray = animatedGif(frames = 1)

    private fun Int.toLo(): Byte = (this and 0xFF).toByte()
    private fun Int.toHi(): Byte = ((this shr 8) and 0xFF).toByte()
}
