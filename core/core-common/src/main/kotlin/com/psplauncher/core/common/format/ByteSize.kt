package com.psplauncher.core.common.format

import java.util.Locale

fun formatByteSize(bytes: Long): String = when {
    bytes < 0L -> "0 B"
    bytes >= GB -> String.format(Locale.US, "%.1f GB", bytes / GB.toDouble())
    bytes >= MB -> String.format(Locale.US, "%.1f MB", bytes / MB.toDouble())
    bytes >= KB -> String.format(Locale.US, "%.0f KB", bytes / KB.toDouble())
    else -> "$bytes B"
}

private const val KB = 1L shl 10
private const val MB = 1L shl 20
private const val GB = 1L shl 30
