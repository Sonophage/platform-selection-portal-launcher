package com.psplauncher.feature.launcher

import android.os.SystemClock
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LaunchDispatcherScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LaunchRealtimeClock

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LaunchWallClock

@Module
@InstallIn(SingletonComponent::class)
object LaunchDispatcherModule {
    @Provides
    @Singleton
    @LaunchDispatcherScope
    fun provideLaunchDispatcherScope(): CoroutineScope =

        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Provides
    @Singleton
    @LaunchRealtimeClock
    fun provideLaunchRealtimeClock(): LaunchClock = LaunchClock { SystemClock.elapsedRealtime() }

    @Provides
    @Singleton
    @LaunchWallClock
    fun provideLaunchWallClock(): LaunchClock = LaunchClock { System.currentTimeMillis() }
}
