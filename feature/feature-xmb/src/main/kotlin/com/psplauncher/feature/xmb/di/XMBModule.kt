package com.psplauncher.feature.xmb.di

import com.psplauncher.core.data.repository.ControllerRegistry
import com.psplauncher.core.data.repository.RemapCoordinator
import com.psplauncher.feature.xmb.gamepad.GamepadInputHandler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object XMBModule {

    @Provides
    @Singleton
    fun provideGamepadInputHandler(
        remapCoordinator: RemapCoordinator,
        registry: ControllerRegistry,
    ): GamepadInputHandler = GamepadInputHandler(remapCoordinator, registry)
}
