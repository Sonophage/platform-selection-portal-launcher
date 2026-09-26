package com.psplauncher.core.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

object MediaDisplayNames {
    const val MAX_LENGTH = 64

    private val cache = HashMap<String, String?>()

    fun queryDisplayName(context: Context, uri: Uri): String? {
        val key = uri.toString()
        cache[key]?.let { return it }
        if (cache.containsKey(key)) return cache[key]

        val queried = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && !cursor.isNull(idx)) cursor.getString(idx) else null
                } else {
                    null
                }
            }
        }.getOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (it.length > MAX_LENGTH) it.take(MAX_LENGTH) + "…" else it }

        cache[key] = queried
        return queried
    }

    fun clearCache() = cache.clear()
}
