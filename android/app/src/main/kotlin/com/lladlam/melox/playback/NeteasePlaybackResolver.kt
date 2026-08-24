package com.lladlam.melox.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.audio.NeteaseQualityClient
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.music.provider.PlaybackAccountStore
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@OptIn(UnstableApi::class)
class NeteasePlaybackResolver(
    private val cookieProvider: () -> String = { "" },
    @Suppress("UNUSED_PARAMETER")
    private val client: NeteaseSearchClient = NeteaseSearchClient(cookieProvider = cookieProvider),
    private val localSourceProvider: (Long) -> Uri? = { null },
) : ResolvingDataSource.Resolver {
    private data class ResolveKey(
        val songId: Long,
        val quality: MusicQuality,
        val cookieHeader: String,
    )

    private val cacheLock = Any()
    private val resolvedUris = object : LinkedHashMap<ResolveKey, Uri>(MAX_RESOLVED_URIS, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ResolveKey, Uri>?): Boolean =
            size > MAX_RESOLVED_URIS
    }
    private val inFlight = ConcurrentHashMap<ResolveKey, CompletableFuture<Uri>>()
    private val qualityClient = NeteaseQualityClient(cookieProvider = cookieProvider)

    @Volatile
    private var providerDelegate: ProviderPlaybackResolver? = null

    /**
     * Resolves the same source used by ExoPlayer for offline analysis. Keeping
     * this path in the resolver guarantees that AutoMix never analyses a
     * different quality (or a stale anonymous URL) from the playing deck.
     */
    fun resolveSongUri(
        songId: Long,
        quality: MusicQuality = MusicQualityRuntime.selected,
    ): Uri {
        localSourceProvider(songId)?.let { return it }
        val cookieHeader = cookieProvider()
        val key = ResolveKey(songId, quality, cookieHeader)
        cached(key)?.let { return it }
        val pending = CompletableFuture<Uri>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return runCatching { existing.get() }
            .getOrElse { throw IOException("Unable to resolve playback source", it.cause ?: it) }
        return try {
            val source = qualityClient.playbackSourceBlocking(
                songId = songId,
                requestedQuality = quality,
            )
            Uri.parse(source.url).also {
                synchronized(cacheLock) { resolvedUris[key] = it }
                pending.complete(it)
            }
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (ProviderPlaybackResolver.isProviderTrackUri(uri)) {
            val delegate = providerDelegate()
                ?: throw IOException("MeloX provider playback runtime is not initialized")
            return delegate.resolveDataSpec(dataSpec)
        }
        if (uri.scheme != MELOX_SCHEME || uri.host != SONG_HOST) {
            return dataSpec
        }

        val songId = uri.lastPathSegment?.toLongOrNull()
            ?: throw IOException("Invalid MeloX song URI: $uri")
        localSourceProvider(songId)?.let { local ->
            return dataSpec.withUri(local)
        }
        val requestedQuality = MusicQuality.fromApiLevel(uri.getQueryParameter(QUALITY_QUERY))
            ?: MusicQualityRuntime.selected
        val currentCookieHeader = cookieProvider()
        val key = ResolveKey(songId, requestedQuality, currentCookieHeader)

        val resolved = resolveSongUri(songId, requestedQuality)
        val cacheKey = playbackCacheKey(songId, requestedQuality, currentCookieHeader)

        return dataSpec.buildUpon()
            .setUri(resolved)
            .setKey(cacheKey)
            .build()
    }

    override fun resolveReportedUri(uri: Uri): Uri {
        if (ProviderPlaybackResolver.isProviderTrackUri(uri)) {
            return providerDelegate()?.resolveReportedUri(uri) ?: uri
        }
        if (uri.scheme != MELOX_SCHEME || uri.host != SONG_HOST) return uri
        val songId = uri.lastPathSegment?.toLongOrNull() ?: return uri
        localSourceProvider(songId)?.let { return it }
        val requestedQuality = MusicQuality.fromApiLevel(uri.getQueryParameter(QUALITY_QUERY))
            ?: MusicQualityRuntime.selected
        val currentCookieHeader = cookieProvider()
        return cached(ResolveKey(songId, requestedQuality, currentCookieHeader)) ?: uri
    }

    fun prefetch(uri: Uri) {
        if (ProviderPlaybackResolver.isProviderTrackUri(uri)) {
            providerDelegate()?.prefetch(uri)
            return
        }
        if (uri.scheme != MELOX_SCHEME || uri.host != SONG_HOST) return
        val songId = uri.lastPathSegment?.toLongOrNull() ?: return
        val quality = MusicQuality.fromApiLevel(uri.getQueryParameter(QUALITY_QUERY))
            ?: MusicQualityRuntime.selected
        resolveSongUri(songId, quality)
    }

    private fun cached(key: ResolveKey): Uri? = synchronized(cacheLock) { resolvedUris[key] }

    private fun providerDelegate(): ProviderPlaybackResolver? {
        providerDelegate?.let { return it }
        val registry = ProviderPlaybackRuntime.registryOrNull() ?: return null
        return synchronized(this) {
            providerDelegate ?: ProviderPlaybackResolver(
                neteaseResolver = this,
                providers = registry,
                authKeyProvider = ProviderPlaybackRuntime::authKey,
            ).also { providerDelegate = it }
        }
    }

    companion object {
        private const val MELOX_SCHEME = "melox"
        private const val SONG_HOST = "song"
        private const val QUALITY_QUERY = "quality"
        private const val MAX_RESOLVED_URIS = 96
        private const val PLAYBACK_CACHE_VERSION = 2

        private fun playbackCacheKey(songId: Long, quality: MusicQuality, cookieHeader: String): String =
            "netease:v$PLAYBACK_CACHE_VERSION:$songId:${quality.apiLevel}:${cookieHeader.hashCode().toUInt().toString(16)}"

        fun uriForSong(
            songId: Long,
            quality: MusicQuality = MusicQualityRuntime.selected,
        ): Uri = Uri.Builder()
            .scheme(MELOX_SCHEME)
            .authority(SONG_HOST)
            .appendPath(songId.toString())
            .appendQueryParameter(QUALITY_QUERY, quality.apiLevel)
            .build()
    }
}
