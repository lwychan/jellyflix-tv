package com.notflix.tv.di

import android.content.Context
import com.notflix.tv.data.BrandingRepository
import com.notflix.tv.data.JellyfinClient
import com.notflix.tv.data.MediaRepository
import com.notflix.tv.data.SessionStore
import com.notflix.tv.data.SettingsStore
import com.notflix.tv.playback.StreamUrlResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideSessionStore(@ApplicationContext ctx: Context): SessionStore = SessionStore(ctx)

    @Provides @Singleton
    fun provideSettingsStore(@ApplicationContext ctx: Context): SettingsStore = SettingsStore(ctx)

    @Provides @Singleton
    fun provideJellyfinClient(@ApplicationContext ctx: Context, store: SessionStore): JellyfinClient =
        JellyfinClient(ctx, store)

    @Provides @Singleton
    fun provideMediaRepo(client: JellyfinClient, store: SessionStore): MediaRepository =
        MediaRepository(client, store)

    @Provides @Singleton
    fun provideBrandingRepo(client: JellyfinClient, store: SessionStore): BrandingRepository =
        BrandingRepository(client, store)

    @Provides @Singleton
    fun provideStreamResolver(
        @ApplicationContext ctx: Context,
        client: JellyfinClient,
        store: SessionStore,
    ): StreamUrlResolver = StreamUrlResolver(ctx, client, store)
}
