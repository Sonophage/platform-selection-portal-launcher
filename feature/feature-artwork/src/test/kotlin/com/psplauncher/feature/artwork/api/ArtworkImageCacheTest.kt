package com.psplauncher.feature.artwork.api

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.BitmapImage
import coil3.asImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.imageLoader
import coil3.memory.MemoryCache
import com.psplauncher.core.ui.image.ArtworkRevisions
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Provider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ArtworkImageCacheTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var configuredLoader: ImageLoader
    private lateinit var cache: ArtworkImageCache

    @Before
    fun setUp() {
        SingletonImageLoader.reset()

        configuredLoader = ArtworkModule.provideCoilImageLoader(context)
        cache = ArtworkImageCache(Provider { configuredLoader })
        cache.installAsSingleton()
    }

    @After
    fun tearDown() {
        SingletonImageLoader.reset()
    }

    @Test
    fun `the loader AsyncImage resolves is the one the cache was given`() {
        assertSame(configuredLoader, context.imageLoader)
    }

    @Test
    fun `the installed loader is the configured artwork loader, not a Coil default`() {
        val directory = context.imageLoader.diskCache?.directory?.toString()
        assertNotNull(directory)
        assertTrue(directory.endsWith("artwork_cache"), "unexpected disk cache location: $directory")
    }

    @Test
    fun `evict drops the uri from the memory cache AsyncImage reads through`() {
        val key = MemoryCache.Key(URI)
        context.imageLoader.memoryCache!![key] = MemoryCache.Value(onePixel())
        assertNotNull(context.imageLoader.memoryCache!![key], "precondition: entry is cached")

        cache.evict(listOf(URI))

        assertNull(context.imageLoader.memoryCache!![key])
    }

    @Test
    fun `evict leaves other entries alone`() {
        val stale = MemoryCache.Key(URI)
        val other = MemoryCache.Key(OTHER_URI)
        context.imageLoader.memoryCache!![stale] = MemoryCache.Value(onePixel())
        context.imageLoader.memoryCache!![other] = MemoryCache.Value(onePixel())

        cache.evict(listOf(URI))

        assertNull(context.imageLoader.memoryCache!![stale])
        assertNotNull(context.imageLoader.memoryCache!![other])
    }

    @Test
    fun `evict drops the uri from the disk cache too`() {
        val disk = context.imageLoader.diskCache!!
        disk.openEditor(URI)!!.apply {
            disk.fileSystem.write(data) { writeUtf8("stale bytes") }
            commit()
        }
        assertNotNull(disk.openSnapshot(URI)?.also { it.close() }, "precondition: entry is on disk")

        cache.evict(listOf(URI))

        assertNull(context.imageLoader.diskCache!!.openSnapshot(URI))
    }

    @Test
    fun `evict bumps each uri's revision, so images already on screen reload`() {
        val uri = "content://artwork/revision-probe.png"
        val before = ArtworkRevisions.of(uri)

        cache.evict(listOf(uri))

        assertEquals(before + 1, ArtworkRevisions.of(uri))
        assertEquals(0, ArtworkRevisions.of("content://artwork/never-evicted.png"))
    }

    @Test
    fun `clear empties the memory cache AsyncImage reads through`() {
        context.imageLoader.memoryCache!![MemoryCache.Key(URI)] = MemoryCache.Value(onePixel())
        context.imageLoader.memoryCache!![MemoryCache.Key(OTHER_URI)] = MemoryCache.Value(onePixel())

        cache.clear()

        assertTrue(context.imageLoader.memoryCache!!.keys.isEmpty())
    }

    @Test
    fun `installAsSingleton is safe to call twice`() {
        cache.installAsSingleton()
        assertSame(configuredLoader, context.imageLoader)
    }

    private fun onePixel(): BitmapImage =
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImage()

    private companion object {
        const val URI = "content://artwork/game-1.png"
        const val OTHER_URI = "content://artwork/game-2.png"
    }
}
