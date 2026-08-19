package com.lladlam.melox.di

import android.content.Context
import com.lladlam.melox.core.account.NeteaseSessionStore
import com.lladlam.melox.core.library.NeteaseLibraryCache
import com.lladlam.melox.core.network.NeteaseAccountDetailsClient
import com.lladlam.melox.core.network.NeteaseCollectionDetailsClient
import com.lladlam.melox.core.network.NeteaseMusicOperationsClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.network.NeteaseSocialExtrasClient
import com.lladlam.melox.core.network.NeteaseUniversalSearchClient
import com.lladlam.melox.core.recognition.SongRecognitionClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NeteaseModule {

    @Provides
    @Named("netease")
    @Singleton
    fun provideNeteaseHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideCookieProvider(@ApplicationContext context: Context): () -> String {
        return { NeteaseSessionStore.readCookie(context) }
    }

    @Provides
    @Singleton
    fun provideNeteaseSearchClient(
        cookieProvider: () -> String,
        @Named("netease") httpClient: OkHttpClient,
    ): NeteaseSearchClient {
        return NeteaseSearchClient(cookieProvider = cookieProvider, httpClient = httpClient)
    }

    @Provides
    @Singleton
    fun provideNeteaseLibraryClient(
        cookieProvider: () -> String,
        @Named("netease") httpClient: OkHttpClient,
    ): com.lladlam.melox.core.library.NeteaseLibraryClient {
        return com.lladlam.melox.core.library.NeteaseLibraryClient(
            cookieProvider = cookieProvider,
            httpClient = httpClient,
        )
    }

    @Provides
    @Singleton
    fun provideNeteaseMusicOperationsClient(
        cookieProvider: () -> String,
        @Named("netease") httpClient: OkHttpClient,
    ): NeteaseMusicOperationsClient {
        return NeteaseMusicOperationsClient(cookieProvider = cookieProvider, httpClient = httpClient)
    }

    @Provides
    @Singleton
    fun provideNeteaseSocialExtrasClient(
        cookieProvider: () -> String,
        @Named("netease") httpClient: OkHttpClient,
    ): NeteaseSocialExtrasClient {
        return NeteaseSocialExtrasClient(cookieProvider = cookieProvider, httpClient = httpClient)
    }

    @Provides
    @Singleton
    fun provideNeteaseCollectionDetailsClient(
        cookieProvider: () -> String,
        @Named("netease") httpClient: OkHttpClient,
    ): NeteaseCollectionDetailsClient {
        return NeteaseCollectionDetailsClient(cookieProvider = cookieProvider, httpClient = httpClient)
    }

    @Provides
    @Singleton
    fun provideNeteaseUniversalSearchClient(
        cookieProvider: () -> String,
        @Named("netease") httpClient: OkHttpClient,
    ): NeteaseUniversalSearchClient {
        return NeteaseUniversalSearchClient(cookieProvider = cookieProvider, httpClient = httpClient)
    }

    @Provides
    @Singleton
    fun provideNeteaseAccountDetailsClient(
        cookieProvider: () -> String,
        @Named("netease") httpClient: OkHttpClient,
    ): NeteaseAccountDetailsClient {
        return NeteaseAccountDetailsClient(cookieProvider = cookieProvider, httpClient = httpClient)
    }

    @Provides
    @Singleton
    fun provideNeteaseLibraryCache(@ApplicationContext context: Context): NeteaseLibraryCache {
        return NeteaseLibraryCache(context)
    }

    @Provides
    @Singleton
    fun provideSongRecognitionClient(
        @ApplicationContext context: Context,
        @Named("netease") httpClient: OkHttpClient,
    ): SongRecognitionClient {
        return SongRecognitionClient(context, httpClient)
    }
}
