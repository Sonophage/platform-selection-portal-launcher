package com.psplauncher.feature.artwork.credentials

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CredentialModule {
    @Binds
    @Singleton
    abstract fun bindMetadataCredentialSource(
        impl: BundledDevPairCredentialSource,
    ): MetadataCredentialSource
}
