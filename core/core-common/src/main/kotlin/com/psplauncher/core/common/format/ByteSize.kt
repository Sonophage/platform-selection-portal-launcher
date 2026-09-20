package com.psplauncher.core.common.format

import java.util.Locale

// ── File sizes, once ──────────────────────────────────────────────────────────
//
// There were four of these, in four files, disagreeing about which tiers exist:
//
//   LibraryRowText.formatBytes   B / KB / MB          no GB
//   ArtworkImportScreen          B / KB / MB / GB     complete
//   PhotoViewerScreen.fmtSize    KB / MB              no B, no GB
//   VideoDetailScreen.fmtSize    MB / GB              no KB -- a 300 KB clip read "0 MB"
//
// None of them was wrong about its own screen when it was written. That is how four of something
// happens. The Video one is the reason this got fixed rather than logged: "0 MB" is not a rounding
// artefact to the person reading it, it is the app saying the file is empty.

/**
 * A human-readable file size (pure — unit-tested).
 *
 * Binary tiers, because these are file sizes reported by the filesystem and every other tool the
 * user will compare them against uses 1024. Whole numbers below a megabyte and one decimal above:
 * "347 KB" is what a size is for, and "347.2 KB" is noise at the scale where it appears.
 *
 * Zero is "0 B", not a dash. A caller that means "unknown" should not be passing zero, and the
 * ones that do already guard it themselves.
 */
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
