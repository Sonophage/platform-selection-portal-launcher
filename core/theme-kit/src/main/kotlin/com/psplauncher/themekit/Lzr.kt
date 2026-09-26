package com.psplauncher.themekit

object Lzr {
    fun decompress(input: ByteArray, offset: Int, length: Int, maxOutput: Int): ByteArray? {
        if (offset < 0 || length < 5 || offset.toLong() + length > input.size) return null
        if (maxOutput !in 0..MAX_OUTPUT_BYTES) return null
        return Decoder(input, offset, offset + length, maxOutput).run()
    }

    const val MAX_OUTPUT_BYTES: Int = 32 * 1024 * 1024

    private const val PROBABILITY_SLOTS = 2800
    private const val INITIAL_PROBABILITY = 0x80
    private const val RENORM_LIMIT = 0x00FFFFFFL
    private const val U32 = 0xFFFFFFFFL

    private class Decoder(
        private val input: ByteArray,
        private val start: Int,
        private val end: Int,
        maxOutput: Int,
    ) {
        private val out = ByteArray(maxOutput)
        private var outPos = 0
        private var inPos = 0

        private var code = 0L
        private var range = U32

        private var spec = 0L

        private val probs = IntArray(PROBABILITY_SLOTS) { INITIAL_PROBABILITY }

        private var corrupt = false

        fun run(): ByteArray? {
            val type = input[start].toInt()
            val header = readBigEndianU32(start + 1)
            inPos = start + 5

            if (type < 0) return storedBlock(length = header)

            code = header
            while (true) {
                val tokenIsMatch = decodeBit(bufOffContext())
                if (corrupt) return null
                if (tokenIsMatch == 0) {
                    if (!decodeLiteral(type)) return null
                } else {
                    when (decodeMatch()) {
                        MatchResult.OK -> Unit
                        MatchResult.END_OF_STREAM -> return out.copyOf(outPos)
                        MatchResult.CORRUPT -> return null
                    }
                }
            }
        }

        private fun storedBlock(length: Long): ByteArray? {
            if (length > out.size || inPos + length > end) return null
            val n = length.toInt()
            input.copyInto(out, 0, inPos, inPos + n)
            return out.copyOf(n)
        }

        private var bufOff = 0
        private var lastByte = 0

        private fun bufOffContext(): Int = bufOff + 2488

        private fun decodeLiteral(shift: Int): Boolean {
            if (bufOff > 0) bufOff--
            if (outPos == out.size) return false
            val context = ((((outPos and 0x07) shl 8) + lastByte) shr shift) and 0x07
            val treeBase = context * 0xFF - 1
            var node = 1
            while (node <= 0xFF) {
                node = (node shl 1) or decodeBit(treeBase + node)
                if (corrupt) return false
            }
            out[outPos++] = node.toByte()
            lastByte = node and 0xFF
            return true
        }

        private enum class MatchResult { OK, END_OF_STREAM, CORRUPT }

        private fun decodeMatch(): MatchResult {
            var slot = bufOffContext()
            spec = range
            var lengthClass = -1
            var bit: Int
            do {
                slot += 8
                bit = decodeBitSpeculative(slot)
                if (corrupt) return MatchResult.CORRUPT
                lengthClass += bit
            } while (bit != 0 && lengthClass < 6)

            var offsetGroup = lengthClass + 2033
            var offsetBias = 64
            val length: Long
            if (bit != 0 || lengthClass >= 0) {
                val lengthContext = (lengthClass shl 5) +
                    (((outPos shl lengthClass) and 0x03) shl 3) + bufOff + 2552
                length = decodeNumber(lengthClass, lengthContext, step = 8)
                if (corrupt) return MatchResult.CORRUPT
                if (length == 0xFFL) return MatchResult.END_OF_STREAM
                if (lastNumberFlag != 0 || lengthClass > 0) {
                    offsetGroup += 56
                    offsetBias = 352
                }
            } else {
                length = 1
            }

            var index = 1
            var offsetClass: Int
            do {
                offsetClass = (index shl 4) - offsetBias
                val probIndex = offsetGroup + (index shl 3)
                if (probIndex >= PROBABILITY_SLOTS) return MatchResult.CORRUPT
                bit = decodeBit(probIndex)
                if (corrupt) return MatchResult.CORRUPT
                index = (index shl 1) or bit
            } while (offsetClass < 0)

            val offset: Long
            if (bit != 0 || offsetClass > 0) {
                if (bit == 0) offsetClass -= 8
                val base = offsetClass + 2344
                if (base < 0 || base + 3 >= PROBABILITY_SLOTS) return MatchResult.CORRUPT
                offset = decodeNumber(offsetClass / 8, base, step = 1)
                if (corrupt) return MatchResult.CORRUPT
            } else {
                offset = 1
            }

            if (offset > outPos) return MatchResult.CORRUPT
            val copyEnd = outPos + length + 1
            if (copyEnd > out.size) return MatchResult.CORRUPT
            bufOff = ((copyEnd.toInt() + 1) and 0x01) + 0x06
            var from = outPos - offset.toInt()
            val until = copyEnd.toInt()
            while (outPos < until) out[outPos++] = out[from++]
            lastByte = out[outPos - 1].toInt() and 0xFF
            return MatchResult.OK
        }

        private var lastNumberFlag = 0

        private fun decodeNumber(bits: Int, base: Int, step: Int): Long {
            var number = 1L
            if (bits >= 3) {
                number = (number shl 1) or decodeBit(base + 3 * step).toLong()
                if (bits >= 4) {
                    number = (number shl 1) or decodeBit(base + 3 * step).toLong()
                    if (bits >= 5) {
                        renormalize()
                        var remaining = bits
                        while (remaining >= 5) {
                            if (range == 0L) { corrupt = true; return 0 }
                            number = number shl 1
                            range = range ushr 1
                            if (code < range) number++ else code -= range
                            remaining--
                        }
                    }
                }
            }
            lastNumberFlag = decodeBit(base)
            number = (number shl 1) or lastNumberFlag.toLong()
            if (bits >= 1) {
                number = (number shl 1) or decodeBit(base + step).toLong()
                if (bits >= 2) {
                    number = (number shl 1) or decodeBit(base + 2 * step).toLong()
                }
            }
            return number
        }

        private fun renormalize() {
            if (range <= RENORM_LIMIT) {
                code = ((code shl 8) or nextByte()) and U32
                range = (range shl 8) and U32
            }
        }

        private fun decodeBit(probIndex: Int): Int {
            renormalize()
            if (corrupt) return 0
            val p = probs[probIndex]
            val bound = (range ushr 8) * p
            probs[probIndex] = p - (p shr 3)
            return if (code < bound) {
                range = bound
                probs[probIndex] += 31
                1
            } else {
                code -= bound
                range -= bound
                0
            }
        }

        private fun decodeBitSpeculative(probIndex: Int): Int {
            if (spec <= RENORM_LIMIT) {
                code = ((code shl 8) or nextByte()) and U32
                range = (spec shl 8) and U32
            }
            if (corrupt) return 0
            val p = probs[probIndex]
            val bound = (range ushr 8) * p
            spec = bound
            probs[probIndex] = p - (p shr 3)
            return if (code < bound) {
                range = bound
                probs[probIndex] += 31
                1
            } else {
                code -= bound
                range -= bound
                0
            }
        }

        private fun nextByte(): Long {
            if (inPos >= end) {
                corrupt = true
                return 0
            }
            return (input[inPos++].toInt() and 0xFF).toLong()
        }

        private fun readBigEndianU32(at: Int): Long =
            ((input[at].toInt() and 0xFF).toLong() shl 24) or
                ((input[at + 1].toInt() and 0xFF).toLong() shl 16) or
                ((input[at + 2].toInt() and 0xFF).toLong() shl 8) or
                (input[at + 3].toInt() and 0xFF).toLong()
    }
}
