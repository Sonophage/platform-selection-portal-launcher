package com.psplauncher.feature.library.scanner

import com.psplauncher.core.domain.model.GameRegion

object DiscRegionDetectors {
    fun detectPsx(head: ByteArray): GameRegion? {
        val marker = head.indexOfAscii("Licensed by Sony Computer Entertainment")
        if (marker >= 0) {
            val after = head.copyOfRange(marker, minOf(marker + 120, head.size))
                .toString(Charsets.ISO_8859_1)
            val region = when {
                after.contains("America", ignoreCase = true) -> GameRegion.NTSC_U
                after.contains("Europe", ignoreCase = true) -> GameRegion.PAL
                after.contains("Inc.", ignoreCase = true) -> GameRegion.NTSC_J
                else -> null
            }
            if (region != null) return region
        }
        return serialRegion(head, PSX_NTSC_U_SERIAL, PSX_PAL_SERIAL, PSX_NTSC_J_SERIAL)
    }

    fun detectPs2(head: ByteArray): GameRegion? {
        val text = head.toString(Charsets.ISO_8859_1)
        val match = Regex("""REGION\s*=\s*(NTSC-U|PAL|NTSC-J)""", RegexOption.IGNORE_CASE).find(text)
        if (match != null) {
            return when (match.groupValues[1].uppercase()) {
                "NTSC-U" -> GameRegion.NTSC_U
                "PAL" -> GameRegion.PAL
                else -> GameRegion.NTSC_J
            }
        }
        return serialRegion(head, PSX_NTSC_U_SERIAL, PSX_PAL_SERIAL, PSX_NTSC_J_SERIAL)
    }

    fun detectPsp(head: ByteArray): GameRegion? {
        val text = head.toString(Charsets.ISO_8859_1)
        return when {
            Regex("""\b(ULUS|UCUS|NPUH|ULAS|UCAS)\s?-\s?\d""").containsMatchIn(text) -> GameRegion.NTSC_U
            Regex("""\b(ULES|UCES|NPEG|ULES)\s?-\s?\d""").containsMatchIn(text) -> GameRegion.PAL
            Regex("""\b(ULJM|UCJS|NPJH)\s?-\s?\d""").containsMatchIn(text) -> GameRegion.NTSC_J
            else -> null
        }
    }

    fun detectBootBin(head: ByteArray): GameRegion? {
        if (head.size < 0x5C) return null
        val id = head.copyOfRange(0, 6).toString(Charsets.ISO_8859_1)
        if (id.any { !it.isLetterOrDigit() }) return null
        if (id.getOrNull(0)?.uppercaseChar() !in setOf('G', 'R', 'S', 'D')) return null
        when (beInt(head, 0x58)) {
            0 -> return GameRegion.NTSC_J
            1 -> return GameRegion.NTSC_U
            2 -> return GameRegion.PAL
        }
        return when (id.getOrNull(3)?.uppercaseChar()) {
            'E' -> GameRegion.NTSC_U
            'J' -> GameRegion.NTSC_J
            'P', 'W', 'D', 'F', 'I', 'S', 'X' -> GameRegion.PAL
            else -> null
        }
    }

    fun detectIpBin(head: ByteArray): GameRegion? {
        val text = head.toString(Charsets.ISO_8859_1)
        val regionByte = when {
            text.startsWith("SEGA SEGAKATANA") -> head.getOrNull(0x10)
            text.startsWith("SEGA SEGASATURN") -> head.getOrNull(0x20)
            else -> return null
        } ?: return null
        return when (regionByte.toInt().toChar().uppercaseChar()) {
            'J' -> GameRegion.NTSC_J
            'T', 'U' -> GameRegion.NTSC_U
            'E' -> GameRegion.PAL
            else -> null
        }
    }

    fun detectX360(head: ByteArray): GameRegion? {
        val start = head.indexOfAscii("XEX2")
        if (start < 0 || start + 0x1C > head.size) return null
        val count = leInt(head, start + 0x18)
        if (count <= 0 || count > 64) return null
        var off = start + 0x1C
        repeat(count) {
            if (off + 8 > head.size) return null
            val size = leInt(head, off)
            val type = leInt(head, off + 4)
            val data = off + 8

            if (size < 8 || off > head.size - size) return null
            if (type == 0x00000001 && data + 0x20 <= head.size) {
                val region = leInt(head, data + 0x1C)
                return when {
                    region and 0x01 != 0 -> GameRegion.NTSC_U
                    region and 0x02 != 0 -> GameRegion.NTSC_J
                    region and 0x04 != 0 -> GameRegion.PAL
                    else -> null
                }
            }
            off += size
        }
        return null
    }

    fun detectPs3Sfo(sfo: ByteArray): GameRegion? {
        val text = sfo.toString(Charsets.ISO_8859_1)
        return when {
            Regex("""\b(BLUS|BCUS|NPUB)\d{5}""").containsMatchIn(text) -> GameRegion.NTSC_U
            Regex("""\b(BLES|BCES|NPEP)\d{5}""").containsMatchIn(text) -> GameRegion.PAL
            Regex("""\b(BLJM|BCJS|NPJB)\d{5}""").containsMatchIn(text) -> GameRegion.NTSC_J
            else -> null
        }
    }

    private val PSX_NTSC_U_SERIAL = Regex("""\b(SLUS|SCUS|PBPX|PAPX)[-_ ]?\d""")
    private val PSX_PAL_SERIAL = Regex("""\b(SLES|SCES|SIPS)[-_ ]?\d""")
    private val PSX_NTSC_J_SERIAL = Regex("""\b(SLPS|SCPS|SLPM|SCPM)[-_ ]?\d""")

    private fun serialRegion(head: ByteArray, ntscU: Regex, pal: Regex, ntscJ: Regex): GameRegion? {
        val text = head.toString(Charsets.ISO_8859_1)
        return when {
            ntscU.containsMatchIn(text) -> GameRegion.NTSC_U
            pal.containsMatchIn(text) -> GameRegion.PAL
            ntscJ.containsMatchIn(text) -> GameRegion.NTSC_J
            else -> null
        }
    }

    private fun ByteArray.indexOfAscii(text: String, from: Int = 0): Int {
        val needle = text.encodeToByteArray()
        if (needle.isEmpty() || needle.size > size - from) return -1
        outer@ for (i in from..size - needle.size) {
            for (j in needle.indices) {
                if (this[i + j] != needle[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun leInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun beInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
}
