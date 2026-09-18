package com.psplauncher.launcher.di

import android.content.Context
import android.content.pm.LauncherApps
import android.os.PowerManager
import android.view.inputmethod.InputMethodManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import com.psplauncher.feature.artwork.api.ArtworkImageCache
import com.psplauncher.feature.library.scanner.RescanApplicationScope
import com.psplauncher.core.data.repository.CustomIconCacheEvictor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // System services used across features
    @Provides
    @Singleton
    fun provideLauncherApps(@ApplicationContext context: Context): LauncherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    @Provides
    @Singleton
    fun providePowerManager(@ApplicationContext context: Context): PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    @Provides
    @Singleton
    @RescanApplicationScope
    fun provideRescanApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // CustomIconStore (core-data) must evict Coil's path-keyed cache when a replaced GIF lands
    // at a stable path — but core-data can't see feature-artwork's ArtworkImageCache. The app
    // module is the one place that sees both, so the seam is bound here.
    @Provides
    @Singleton
    fun provideCustomIconCacheEvictor(imageCache: ArtworkImageCache): CustomIconCacheEvictor =
        CustomIconCacheEvictor { path -> imageCache.evict(path) }
}
