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

/** Coroutine scope the launch dispatcher runs its verification timers on (app-scoped). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LaunchDispatcherScope

/** Monotonic elapsed-realtime clock for launch-verification windows (injectable in tests). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LaunchRealtimeClock

/**
 * Wall clock, for the instants that get WRITTEN DOWN.
 *
 * Two clocks because they answer two questions and neither can do the other's job. Elapsed
 * realtime measures an interval and cannot be moved by the user or a time sync, which is what the
 * verification window needs. `currentTimeMillis` names a moment other rows can be compared
 * against, which is what a timestamp in a database needs.
 *
 * Using the monotonic one for a timestamp is not a rounding error, it is a different number
 * entirely: a few hours of uptime against ~1.79e12 since the epoch. Written into
 * `games.last_played_at` it sorts below every row that used the wall clock, which is exactly what
 * a game did -- it went to the BOTTOM of the Last Played shelf when it was played.
 */
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
        // Main.immediate: [LaunchDispatcher] mutates its pending state from MainActivity's
        // lifecycle callbacks (main thread) and from ViewModel launch sites (also main). Confining
        // the dispatcher's own jobs to the main thread makes those transitions race-free by
        // construction — no mutex needed for a tiny, low-frequency state machine.
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
