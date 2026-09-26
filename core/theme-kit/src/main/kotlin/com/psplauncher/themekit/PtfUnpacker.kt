package com.psplauncher.themekit

object PtfUnpacker {
    class Resource(
        val slotId: Int,
        val sequence: Int,
        val kind: Kind,

        val payload: ByteArray?,
        val image: BmpImage?,
    ) {
        enum class Kind { GIM, BMP, OTHER, FAILED }
    }

    class Dump(
        val name: String,
        val firmware: String,
        val resources: List<Resource>,
    )

    private const val RECORD_HEADER = 32
    private const val METHOD_LZR = 1
    private const val METHOD_ZLIB = 2

    private const val MAX_TOTAL_OUTPUT_BYTES = 256L * 1024 * 1024

    fun unpack(bytes: ByteArray): Dump? {
        val theme = PtfParser.parse(bytes) ?: return null
        val resources = mutableListOf<Resource>()
        var totalOutput = 0L

        for (slot in theme.slots) {
            val end = (slot.dataOffset.toLong() + slot.size).coerceAtMost(bytes.size.toLong()).toInt()
            var cursor = slot.dataOffset
            if (cursor < 0) continue
            while (cursor + RECORD_HEADER <= end) {
                val sequence = bytes.i32(cursor)
                val type = bytes.u16(cursor + 4)
                val method = bytes.u16(cursor + 6)
                val compressed = bytes.i32(cursor + 8)
                val uncompressed = bytes.i32(cursor + 12)

                if (type !in 4..5 || compressed <= 0 || compressed > end - cursor - RECORD_HEADER) break
                if (uncompressed !in 1..PtfParser.MAX_INFLATED_BYTES) break
                totalOutput += uncompressed
                if (totalOutput > MAX_TOTAL_OUTPUT_BYTES) {
                    return Dump(name = theme.name, firmware = theme.firmware, resources = resources)
                }

                val dataAt = cursor + RECORD_HEADER
                val payload = when {
                    compressed == uncompressed -> bytes.copyOfRange(dataAt, dataAt + compressed)
                    method == METHOD_LZR -> Lzr.decompress(bytes, dataAt, compressed, uncompressed)
                    method == METHOD_ZLIB -> PtfParser.inflate(bytes, dataAt, compressed)
                    else -> null
                }
                resources += describe(slot.id, sequence, payload)
                cursor += RECORD_HEADER + compressed
            }
        }
        return Dump(name = theme.name, firmware = theme.firmware, resources = resources)
    }

    private fun describe(slotId: Int, sequence: Int, payload: ByteArray?): Resource {
        if (payload == null) return Resource(slotId, sequence, Resource.Kind.FAILED, null, null)
        return when {
            Gim.isGim(payload) ->
                Resource(slotId, sequence, Resource.Kind.GIM, payload, Gim.decode(payload))
            payload.size >= 2 && payload[0] == 'B'.code.toByte() && payload[1] == 'M'.code.toByte() ->
                Resource(slotId, sequence, Resource.Kind.BMP, payload, Bmp.decode(payload))
            else -> Resource(slotId, sequence, Resource.Kind.OTHER, payload, null)
        }
    }

    private fun ByteArray.u16(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.i32(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
}
