package com.psplauncher.feature.artwork.api

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.psplauncher.core.ui.image.ArtworkRevisions
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class ArtworkImageCache @Inject constructor(

    private val imageLoader: Provider<ImageLoader>,
) {
    fun installAsSingleton() {
        SingletonImageLoader.setSafe { imageLoader.get() }
    }

    fun evict(uris: Collection<String>) {
        val loader = imageLoader.get()
        uris.forEach { uri ->
            loader.memoryCache?.remove(MemoryCache.Key(uri))
            loader.diskCache?.remove(uri)
            ArtworkRevisions.bump(uri)
        }
    }

    fun evict(uri: String) = evict(listOf(uri))

    fun diskSizeBytes(): Long = imageLoader.get().diskCache?.size ?: 0L

    fun clear() {
        val loader = imageLoader.get()
        loader.diskCache?.clear()
        loader.memoryCache?.clear()
    }
}
