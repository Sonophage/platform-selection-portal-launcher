package com.psplauncher.feature.settings.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

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
