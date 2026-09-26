package com.psplauncher.themekit

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HostileInputTest {
    @Test
    fun `zlib bomb in the wallpaper slot is rejected, not inflated to OOM`() {
        val bombPayload = TestFixtures.zlib(ByteArray(64 * 1024 * 1024))

        val small = TestFixtures.buildPtf("Bomb", "6.20", TestFixtures.buildBmp(2, 2) { _, _ -> 0 })

        val dataOffset = 0x140
        val leadIn = 32
        val file = ByteArray(dataOffset + leadIn + bombPayload.size)
        small.copyInto(file, 0, 0, dataOffset)

        val slotSize = leadIn + bombPayload.size
        file[0x124] = (slotSize and 0xFF).toByte()
        file[0x125] = (slotSize shr 8 and 0xFF).toByte()
        file[0x126] = (slotSize shr 16 and 0xFF).toByte()
        file[0x127] = (slotSize shr 24 and 0xFF).toByte()
        bombPayload.copyInto(file, dataOffset + leadIn)

        val theme = assertNotNull(PtfParser.parse(file))
        assertNull(theme.wallpaper, "bomb wallpaper must be rejected")
    }

    private fun ptfWithSlotPointer(pointer: Long): ByteArray {
        val file = ByteArray(0x140)
        file[0] = 0; file[1] = 'P'.code.toByte(); file[2] = 'T'.code.toByte(); file[3] = 'F'.code.toByte()
        val table = 0x100
        file[table]     = (pointer and 0xFF).toByte()
        file[table + 1] = (pointer shr 8 and 0xFF).toByte()
        file[table + 2] = (pointer shr 16 and 0xFF).toByte()
        file[table + 3] = (pointer shr 24 and 0xFF).toByte()
        return file
    }

    @Test
    fun `a slot pointer of 0xFFFFFFFF does not throw`() {
        val theme = PtfParser.parse(ptfWithSlotPointer(0xFFFFFFFFL))

        assertNull(theme?.wallpaper)
    }

    @Test
    fun `a slot pointer with the high bit set does not throw`() {
        val theme = PtfParser.parse(ptfWithSlotPointer(0x80000000L))

        assertNull(theme?.wallpaper)
    }

    @Test
    fun `a slot pointer past the end of the file is skipped`() {
        val theme = PtfParser.parse(ptfWithSlotPointer(0x7FFFFFF0L))

        assertNull(theme?.wallpaper)
    }

    @Test
    fun `a truncated gim does not throw`() {
        val gim = ByteArray(40)
        "MIG.00.1PSP".toByteArray(Charsets.ISO_8859_1).copyInto(gim)

        assertNull(Gim.decode(gim))
    }

    @Test
    fun `a gim chunk size that overflows the walk offset does not throw`() {
        val gim = ByteArray(64)
        "MIG.00.1PSP".toByteArray(Charsets.ISO_8859_1).copyInto(gim)

        gim[16] = 0x99.toByte(); gim[17] = 0
        gim[20] = 0xF0.toByte(); gim[21] = 0xFF.toByte(); gim[22] = 0xFF.toByte(); gim[23] = 0x7F

        assertNull(Gim.decode(gim))
    }

    @Test
    fun `a gim chunk size of zero terminates instead of spinning`() {
        val gim = ByteArray(64)
        "MIG.00.1PSP".toByteArray(Charsets.ISO_8859_1).copyInto(gim)
        gim[16] = 0x99.toByte()

        assertNull(Gim.decode(gim))
    }

    @Test
    fun `bmp with absurd dimensions is rejected before allocation`() {
        val header = ByteArray(64)
        header[0] = 'B'.code.toByte(); header[1] = 'M'.code.toByte()
        header.putU32(10, 54)
        header.putU32(14, 40)
        header.putU32(18, 800_000_000)
        header.putU32(22, 2)
        header.putU16(26, 1)
        header.putU16(28, 24)
        assertNull(Bmp.decode(header))

        header.putU32(18, 20_000)
        assertNull(Bmp.decode(header))
    }

    @Test
    fun `pfptheme zip bomb entry is rejected`() {
        val zip = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { z ->
                z.putNextEntry(ZipEntry("manifest.json"))
                z.write("""{"manifest":"pfptheme","schemaVersion":1,"name":"Bomb","accentColor":"#FFFFFF"}""".toByteArray())
                z.closeEntry()
                z.putNextEntry(ZipEntry("wallpaper.png"))
                val chunk = ByteArray(1024 * 1024)
                repeat(96) { z.write(chunk) }
                z.closeEntry()
            }
        }.toByteArray()
        assertNull(PfpThemeCodec.read(zip))
    }

    private fun ByteArray.putU16(offset: Int, value: Int) {
        this[offset] = (value and 0xFF).toByte()
        this[offset + 1] = (value shr 8 and 0xFF).toByte()
    }

    private fun ByteArray.putU32(offset: Int, value: Int) {
        putU16(offset, value and 0xFFFF)
        putU16(offset + 2, value ushr 16)
    }
}
