package com.psplauncher.feature.settings.debug

import java.io.InputStream

data class DebugCredentialsResult(
    val status: String,

    val anyUnprotected: Boolean,
)

const val DEBUG_CREDENTIALS_MAX_BYTES = 64 * 1024

fun readCredentialsText(input: InputStream, maxBytes: Int = DEBUG_CREDENTIALS_MAX_BYTES): String? {
    val buffer = ByteArray(maxBytes + 1)
    var filled = 0
    while (filled < buffer.size) {
        val n = input.read(buffer, filled, buffer.size - filled)
        if (n < 0) break
        filled += n
    }
    if (filled > maxBytes) return null
    return String(buffer, 0, filled, Charsets.UTF_8)
}
