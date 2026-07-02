package com.p2pmessenger.di

import com.p2pmessenger.media.MediaTransferManager
import com.p2pmessenger.media.MediaTransferManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MediaModule {
    @Binds
    @Singleton
    abstract fun bindMediaTransferManager(impl: MediaTransferManagerImpl): MediaTransferManager
}
