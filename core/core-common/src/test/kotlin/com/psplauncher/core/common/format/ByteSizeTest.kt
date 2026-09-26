package com.psplauncher.core.common.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeTest {
    @Test
    fun `small sizes keep their unit instead of rounding to zero`() {
        assertEquals("300 KB", formatByteSize(300L * 1024))
        assertEquals("1 KB", formatByteSize(1024))
        assertEquals("512 B", formatByteSize(512))
        assertEquals("1 B", formatByteSize(1))
    }

    @Test
    fun `each tier starts exactly where the one below it ends`() {
        assertEquals("1023 B", formatByteSize(1023))
        assertEquals("1 KB", formatByteSize(1024))
        assertEquals("1023 KB", formatByteSize((1L shl 20) - 1024))
        assertEquals("1.0 MB", formatByteSize(1L shl 20))

        assertEquals("1024 KB", formatByteSize((1L shl 20) - 1))
        assertEquals("1.0 GB", formatByteSize(1L shl 30))
    }

    @Test
    fun `a large file reaches gigabytes rather than a five figure megabyte count`() {
        assertEquals("4.0 GB", formatByteSize(4L * (1L shl 30)))
        assertEquals("1.5 GB", formatByteSize((1.5 * (1L shl 30)).toLong()))
    }

    @Test
    fun `zero is zero bytes, not a dash and not an empty string`() {
        assertEquals("0 B", formatByteSize(0))
    }

    @Test
    fun `a negative size is clamped rather than printed`() {
        assertEquals("0 B", formatByteSize(-1))
        assertEquals("0 B", formatByteSize(Long.MIN_VALUE))
    }

    @Test
    fun `the decimal separator does not follow the device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.5 GB", formatByteSize((1.5 * (1L shl 30)).toLong()))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
