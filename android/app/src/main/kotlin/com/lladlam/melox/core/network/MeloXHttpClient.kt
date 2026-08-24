package com.lladlam.melox.core.network

import android.content.Context
import java.io.File
import okhttp3.Cache
import okhttp3.OkHttpClient

/** One process-wide connection pool, dispatcher and bounded HTTP cache. */
object MeloXHttpClient {
    private val base = OkHttpClient.Builder().build()

    @Volatile
    private var client: OkHttpClient = base

    val shared: OkHttpClient
        get() = client

    fun initialize(context: Context) {
        if (client.cache != null) return
        synchronized(this) {
            if (client.cache != null) return
            val directory = File(context.applicationContext.cacheDir, "melox_http")
            client = base.newBuilder()
                .cache(Cache(directory, HTTP_CACHE_BYTES))
                .build()
        }
    }

    fun clearCache() {
        client.cache?.evictAll()
    }

    private const val HTTP_CACHE_BYTES = 64L * 1024L * 1024L
}
