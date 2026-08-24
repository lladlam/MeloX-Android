package com.lladlam.melox.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/** Process-wide bounded playback cache used by Media3 for replay and seeking. */
@OptIn(UnstableApi::class)
object MeloXMediaCache {
    @Volatile
    private var cache: SimpleCache? = null

    fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "melox_media"),
            LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_BYTES),
            StandaloneDatabaseProvider(context.applicationContext),
        ).also { cache = it }
    }

    fun clear(context: Context) {
        val active = get(context)
        active.keys.toList().forEach(active::removeResource)
    }

    private const val MEDIA_CACHE_BYTES = 512L * 1024L * 1024L
}
