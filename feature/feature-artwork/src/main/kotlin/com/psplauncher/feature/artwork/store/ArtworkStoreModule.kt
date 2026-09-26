package com.psplauncher.feature.artwork.store

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ArtworkStoreModule {
    @Binds
    abstract fun bindArtworkStore(impl: RoutingArtworkStore): ArtworkStore
}
