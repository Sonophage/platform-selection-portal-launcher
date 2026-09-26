package com.psplauncher.core.data.repository

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream
import java.io.OutputStream

object SafeMedia {
    const val MAX_THEME_FILE_BYTES = 64L * 1024 * 1024

    const val MAX_IMAGE_DIMENSION = 8192

    fun InputStream.copyCappedTo(out: OutputStream, cap: Long = MAX_THEME_FILE_BYTES): Long? {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            if (total > cap) return null
            out.write(buffer, 0, n)
        }
        return total
    }

    fun InputStream.readCapped(cap: Long = MAX_THEME_FILE_BYTES): ByteArray? {
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

    fun decodeBitmapCapped(
        bytes: ByteArray,
        maxDimension: Int = MAX_IMAGE_DIMENSION,
        targetDimension: Int = 1920,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || w > maxDimension || h > maxDimension) return null

        var sample = 1
        while (maxOf(w, h) / (sample * 2) >= targetDimension) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
    }

    fun decodeFileCapped(path: String, maxDimension: Int, targetDimension: Int = maxDimension): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || w > maxDimension || h > maxDimension) return null
        var sample = 1
        while (maxOf(w, h) / (sample * 2) >= targetDimension) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
    }
}
