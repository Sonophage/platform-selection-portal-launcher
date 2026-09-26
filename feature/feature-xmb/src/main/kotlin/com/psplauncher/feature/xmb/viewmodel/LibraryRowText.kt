package com.psplauncher.feature.xmb.viewmodel

import com.psplauncher.core.common.format.formatByteSize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val DOT = "  ·  "

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

internal fun countLabel(count: Int, singular: String, plural: String = singular + "s"): String =
    "$count ${if (count == 1) singular else plural}"

private fun join(vararg parts: String?): String? =
    parts.filterNotNull().joinToString(DOT).takeIf { it.isNotBlank() }

internal fun musicRowSubtitle(artist: String?, album: String?, durationMs: Long?): String? =
    join(artist.clean(), album.clean(), durationMs?.let(::formatDuration).clean())

internal fun videoRowSubtitle(
    durationMs: Long?,
    resolution: String?,
    sizeBytes: Long?,
): String? = join(
    durationMs?.let(::formatDuration).clean(),
    resolution.clean(),
    sizeBytes?.takeIf { it > 0 }?.let(::formatByteSize),
)

internal fun bookRowSubtitle(author: String?, series: String?, seriesIndex: Double?): String? {
    val seriesLabel = series.clean()?.let { name ->

        val index = seriesIndex?.let { i ->
            if (i == Math.floor(i)) "#${i.toInt()}" else "#$i"
        }
        listOfNotNull(name, index).joinToString(" ")
    }
    return join(author.clean(), seriesLabel)
}

internal fun photoRowSubtitle(dateMs: Long?, resolution: String?, sizeBytes: Long?): String? = join(
    dateMs?.takeIf { it > 0 }?.let(::formatDate),
    resolution.clean(),
    sizeBytes?.takeIf { it > 0 }?.let(::formatByteSize),
)

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun formatDate(ms: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(ms))

internal fun relativeDate(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val days = daysSince(epochMillis, now)
    return when {
        days < 0L -> formatDate(epochMillis)
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days <= 30L -> "$days days ago"
        else -> formatDate(epochMillis)
    }
}

private fun daysSince(epochMillis: Long, now: Long): Long = (now - epochMillis) / 86_400_000L

internal fun relativeDateTime(epochMillis: Long, now: Long = System.currentTimeMillis()): String {
    val day = relativeDate(epochMillis, now)
    if (daysSince(epochMillis, now) != 0L) return day
    return "$day, " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
}

internal fun gameMetaLine(
    platform: String,
    lastPlayedAt: Long?,
    publisher: String?,
    now: Long = System.currentTimeMillis(),
): String {
    val tail = lastPlayedAt?.takeIf { it > 0L }?.let { relativeDateTime(it, now) }
        ?: publisher?.takeIf { it.isNotBlank() }
    return if (tail != null) "$platform  ·  $tail" else platform
}

internal fun videoProgressFraction(resumePositionMs: Long, durationMs: Long?): Float? {
    if (resumePositionMs <= 0L) return null
    val duration = durationMs?.takeIf { it > 0L } ?: return null
    return (resumePositionMs.toFloat() / duration).takeIf { it < 1f }
}

internal fun videoProgressLabel(resumePositionMs: Long, durationMs: Long?): String? {
    val duration = durationMs?.takeIf { it > 0L } ?: return null
    if (videoProgressFraction(resumePositionMs, durationMs) == null) return null
    val remainingMin = ((duration - resumePositionMs) / 60_000L).toInt()
    return when {
        remainingMin <= 0 -> "Almost finished"
        remainingMin < 60 -> "$remainingMin min left"
        else -> {
            val h = remainingMin / 60
            val m = remainingMin % 60
            if (m == 0) "$h hr left" else "$h hr $m min left"
        }
    }
}
