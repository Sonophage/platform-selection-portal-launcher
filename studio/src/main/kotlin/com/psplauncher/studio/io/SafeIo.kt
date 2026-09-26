package com.psplauncher.studio.io

import java.io.File
import java.io.InputStream

object SafeIo {
    const val MAX_THEME_FILE_BYTES = 64L * 1024 * 1024

    fun readBytesCapped(file: File, cap: Long = MAX_THEME_FILE_BYTES): ByteArray? {
        if (!file.isFile || file.length() > cap) return null
        return file.inputStream().use { it.readCapped(cap) }
    }

    fun InputStream.readCapped(cap: Long): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > cap) return null
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
