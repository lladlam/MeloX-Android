package com.lladlam.melox.core.plugin

import android.content.Context
import android.graphics.BitmapFactory
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageResult
import coil3.request.Options
import com.lladlam.melox.core.music.provider.MeloXMusicProviders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.Buffer

internal class PluginArtworkFetcher(private val context: Context, private val data: String, private val options: Options) : Fetcher {
    override suspend fun fetch(): SourceFetchResult = slots.withPermit {
        runInterruptible(Dispatchers.IO) {
            val request = PluginArtwork.parse(data)
            val registry = MeloXMusicProviders.create(context)
            val generation = registry.generation
            val provider = registry[request.source] as? PluginMusicProvider ?: error("artwork source unavailable")
            val bytes = PluginHttpBroker(provider.manifest, accountCurrent = {
                registry.generation == generation && registry[request.source] === provider
            }).use { it.imageBytes(request.url) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            require(bounds.outWidth in 1..4096 && bounds.outHeight in 1..4096 && bounds.outWidth.toLong() * bounds.outHeight <= 8_000_000) { "artwork dimensions exceeded" }
            SourceFetchResult(ImageSource(Buffer().write(bytes), options.fileSystem), bounds.outMimeType, DataSource.NETWORK)
        }
    }
    class Factory(private val context: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "melox-plugin-image") PluginArtworkFetcher(context, data.toString(), options) else null
    }
    /** Intercepts before memory-cache lookup, so disable/uninstall cannot be bypassed by an image cache hit. */
    class Guard(private val context: Context) : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val data = chain.request.data.toString()
            if (!data.startsWith("melox-plugin-image:")) return chain.proceed()
            val request = PluginArtwork.parse(data)
            val registry = MeloXMusicProviders.create(context)
            val provider = registry[request.source] ?: error("artwork source unavailable")
            val generation = registry.generation
            val result = chain.withRequest(chain.request.newBuilder()
                .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()).proceed()
            check(registry.generation == generation && registry[request.source] === provider) { "artwork source changed" }
            return result
        }
    }
    companion object { private val slots = Semaphore(4) }
}
