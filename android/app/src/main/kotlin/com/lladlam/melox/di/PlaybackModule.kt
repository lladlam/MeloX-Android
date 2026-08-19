package com.lladlam.melox.di

import android.content.Context
import com.lladlam.melox.core.download.MeloXDownloadStore
import com.lladlam.melox.core.music.provider.MusicProviderRegistry
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import com.lladlam.melox.data.db.MeloXDatabase
import com.lladlam.melox.data.db.dao.CachedLyricDao
import com.lladlam.melox.data.db.dao.DownloadDao
import com.lladlam.melox.data.db.dao.PlaybackCountDao
import com.lladlam.melox.data.db.dao.PlaybackHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {

    @Provides
    @Singleton
    fun provideDownloadStore(@ApplicationContext context: Context): MeloXDownloadStore {
        return MeloXDownloadStore.get(context)
    }

    @Provides
    @Singleton
    fun provideMusicProviderRegistry(
        @ApplicationContext context: Context,
        @Named("netease") httpClient: OkHttpClient,
    ): MusicProviderRegistry {
        return MeloXMusicProviders.create(context, httpClient)
    }

    @Provides
    @Singleton
    fun provideMeloXDatabase(@ApplicationContext context: Context): MeloXDatabase {
        return MeloXDatabase.create(context)
    }

    @Provides
    fun provideDownloadDao(db: MeloXDatabase): DownloadDao = db.downloadDao()

    @Provides
    fun provideCachedLyricDao(db: MeloXDatabase): CachedLyricDao = db.cachedLyricDao()

    @Provides
    fun providePlaybackHistoryDao(db: MeloXDatabase): PlaybackHistoryDao = db.playbackHistoryDao()

    @Provides
    fun providePlaybackCountDao(db: MeloXDatabase): PlaybackCountDao = db.playbackCountDao()
}
