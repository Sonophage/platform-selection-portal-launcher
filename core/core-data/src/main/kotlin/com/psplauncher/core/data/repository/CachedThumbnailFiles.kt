package com.psplauncher.core.data.repository

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

internal suspend fun deleteOrphanedThumbnails(
    thumbnailUris: Collection<String>,
    stillReferenced: suspend (String) -> Boolean,
) {
    if (thumbnailUris.isEmpty()) return
    withContext(Dispatchers.IO) {
        var deleted = 0
        for (uriStr in thumbnailUris.distinct()) {
            runCatching {
                val uri = Uri.parse(uriStr)
                if (uri.scheme != "file") return@runCatching
                if (stillReferenced(uriStr)) return@runCatching
                val path = uri.path ?: return@runCatching
                if (File(path).delete()) deleted++
            }.onFailure { Timber.w(it, "Could not delete cached thumbnail") }
        }
        if (deleted > 0) Timber.i("Deleted $deleted orphaned cached thumbnail(s)")
    }
}
