package com.psplauncher.feature.settings.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Where a settings ViewModel's disk work goes.
 *
 * Same shape as feature-launcher's @ProfileIoDispatcher / @ScannerIoDispatcher rather than a new
 * idea: a qualifier exists so a TEST can hand in its own scheduler, which is the only reason any
 * of them exist. A Kotlin default value cannot do this job -- Dagger reads the constructor, not
 * the defaults, and a plain `CoroutineDispatcher` parameter fails the build with MissingBinding.
 *
 * It was worth adding because DisplaySettingsViewModel built `uiState` at CONSTRUCTION
 * (`stateIn` in a property initialiser), so there is no later moment at which a test could
 * substitute the dispatcher. It has to arrive through the constructor or not at all.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SettingsIoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object SettingsDispatcherModule {

    @Provides
    @Singleton
    @SettingsIoDispatcher
    fun provideSettingsIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}
