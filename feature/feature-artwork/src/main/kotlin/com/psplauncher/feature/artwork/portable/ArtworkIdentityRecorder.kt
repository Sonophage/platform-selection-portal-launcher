package com.psplauncher.feature.artwork.portable

import android.net.Uri
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkIdentityRecorder @Inject constructor(
    private val library: PortableArtworkLibrary,
) {
    private val mutex = Mutex()

    private var loadedFor: String? = null

    private var folderIndex: ArtworkIdentityIndex = ArtworkIdentityIndex()

    private var recorded: List<ArtworkIdentityIndex.Entry> = emptyList()

    private var unreadable = false

    suspend fun record(tree: Uri, entry: ArtworkIdentityIndex.Entry) = mutex.withLock {
        ensureLoaded(tree)
        recorded = recorded + entry
    }

    suspend fun recordAll(tree: Uri, entries: List<ArtworkIdentityIndex.Entry>) = mutex.withLock {
        if (entries.isEmpty()) return@withLock
        ensureLoaded(tree)
        recorded = recorded + entries
    }

    suspend fun flush(tree: Uri): Boolean = mutex.withLock {
        ensureLoaded(tree)
        if (unreadable) return@withLock false
        if (recorded.isEmpty()) return@withLock true
        val merged = folderIndex.upsertAll(recorded)

        if (merged.entries == folderIndex.entries) {
            recorded = emptyList()
            return@withLock true
        }
        val ok = library.writeIdentityIndex(tree, merged)
        if (ok) {
            folderIndex = merged
            recorded = emptyList()
        }
        ok
    }

    suspend fun current(tree: Uri): ArtworkIdentityIndex = mutex.withLock {
        ensureLoaded(tree)
        folderIndex.upsertAll(recorded)
    }

    private suspend fun ensureLoaded(tree: Uri) {
        val key = tree.toString()
        if (loadedFor == key && !unreadable) return
        when (val read = library.readIdentityIndex(tree)) {
            is PortableArtworkLibrary.IdentityIndexRead.Absent -> {
                folderIndex = ArtworkIdentityIndex()
                unreadable = false
            }
            is PortableArtworkLibrary.IdentityIndexRead.Loaded -> {
                folderIndex = read.index
                unreadable = false
            }
            is PortableArtworkLibrary.IdentityIndexRead.Unreadable -> {
                unreadable = true
            }
        }
        loadedFor = key
    }
}
