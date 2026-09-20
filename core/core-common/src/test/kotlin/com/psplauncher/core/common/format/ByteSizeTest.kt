package com.psplauncher.core.common.format

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The tiers, and the boundaries between them.
 *
 * This exists because four hand-written copies of this function disagreed about which tiers even
 * exist, and the disagreement was invisible: each looked right on the screen it was written for.
 * The 300 KB case is the one that shipped wrong -- VideoDetailScreen had no KB tier, so a small
 * clip reported "0 MB", which reads as a broken file rather than a rounding choice.
 */
class ByteSizeTest {

    @Test
    fun `small sizes keep their unit instead of rounding to zero`() {
        // The bug that prompted this. A 300 KB video must not read "0 MB".
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
        // One byte short of a megabyte rounds UP to "1024 KB" rather than showing 1023.9. Pinned
        // because it looks like a tier that was missed, and is not: whole-number KB is the
        // deliberate choice, and this is the one input where it reads oddly.
        assertEquals("1024 KB", formatByteSize((1L shl 20) - 1))
        assertEquals("1.0 GB", formatByteSize(1L shl 30))
    }

    @Test
    fun `a large file reaches gigabytes rather than a five figure megabyte count`() {
        // LibraryRowText's copy had no GB tier, so a 4 GB ISO read "4096.0 MB".
        assertEquals("4.0 GB", formatByteSize(4L * (1L shl 30)))
        assertEquals("1.5 GB", formatByteSize((1.5 * (1L shl 30)).toLong()))
    }

    @Test
    fun `zero is zero bytes, not a dash and not an empty string`() {
        // One of the four returned "—" here. That belongs to the caller that means "unknown";
        // a formatter asked to format zero should say zero.
        assertEquals("0 B", formatByteSize(0))
    }

    @Test
    fun `a negative size is clamped rather than printed`() {
        // The control, and a real possibility: a size column that was never written comes back
        // as -1 from more than one Android API. "-1 B" on screen helps nobody.
        assertEquals("0 B", formatByteSize(-1))
        assertEquals("0 B", formatByteSize(Long.MIN_VALUE))
    }

    @Test
    fun `the decimal separator does not follow the device locale`() {
        // A size string is compared against other tools, and a comma decimal in "1,5 GB" reads as
        // a thousands separator to half the people who see it. Locale.US is pinned in the source;
        // this asserts the consequence rather than the line.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.5 GB", formatByteSize((1.5 * (1L shl 30)).toLong()))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
