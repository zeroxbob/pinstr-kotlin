package com.fibelatti.pinboard.features.nostr.di

import com.fibelatti.pinboard.features.nostr.data.NostrClient
import com.fibelatti.pinboard.features.nostr.data.NostrEventMapper
import com.fibelatti.pinboard.features.nostr.data.PostsDataSourceNostrApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NostrModule {

    @Provides
    @Singleton
    fun provideNostrClient(): NostrClient = NostrClient()

    @Provides
    fun provideNostrEventMapper(): NostrEventMapper = NostrEventMapper()
}
