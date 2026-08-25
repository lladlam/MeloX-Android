package com.lladlam.melox.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.audio.NeteaseQualityClient
import com.lladlam.melox.core.audio.NeteasePlaybackUnavailableException
import com.lladlam.melox.core.music.model.AudioQualityTier
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
    private val crossProviderFallback: CrossProviderPlaybackFallbackResolver? = null,
    private val providerPlaybackEnabled: (com.lladlam.melox.core.music.model.MusicSource) -> Boolean = { true },
) : ResolvingDataSource.Resolver {
    private data class ResolveKey(
        val songId: Long,
        val quality: MusicQuality,
        val cookieHeader: String,
        val fallbackIdentity: String,
        val metadataIdentity: String,
    )
    private data class ResolvedRequest(
        val uri: Uri,
        val headers: Map<String, String> = emptyMap(),
        val expiresAtEpochMs: Long? = null,
        val cacheIdentity: String = "netease",
    )

    private val cacheLock = Any()
    private val resolvedUris = object : LinkedHashMap<ResolveKey, ResolvedRequest>(MAX_RESOLVED_URIS, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ResolveKey, ResolvedRequest>?): Boolean =
            size > MAX_RESOLVED_URIS
    }
    private val inFlight = ConcurrentHashMap<ResolveKey, CompletableFuture<ResolvedRequest>>()
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
    ): Uri = resolveSongRequest(songId, quality, fallbackRequest = null).uri

    private fun resolveSongRequest(
        songId: Long,
        quality: MusicQuality,
        fallbackRequest: CrossProviderFallbackRequest?,
    ): ResolvedRequest {
        localSourceProvider(songId)?.let { return ResolvedRequest(it) }
        val cookieHeader = cookieProvider()
        val key = ResolveKey(
            songId = songId,
            quality = quality,
            cookieHeader = cookieHeader,
            fallbackIdentity = crossProviderFallback?.cacheIdentity().orEmpty(),
            metadataIdentity = fallbackRequest?.let {
                "${it.title}\u001f${it.artist}\u001f${it.durationMs.orZero()}"
            }.orEmpty(),
        )
        cached(key)?.let { return it }
        val pending = CompletableFuture<ResolvedRequest>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return runCatching { existing.get() }
            .getOrElse { throw IOException("Unable to resolve playback source", it.cause ?: it) }
        return try {
            val resolved = try {
                val source = qualityClient.playbackSourceBlocking(
                    songId = songId,
                    requestedQuality = quality,
                )
                if (quality == MusicQualityRuntime.selected) {
                    CrossProviderPlaybackRuntime.clear(songId)
                }
                ResolvedRequest(Uri.parse(source.url))
            } catch (error: NeteasePlaybackUnavailableException) {
                val fallback = fallbackRequest
                    ?.copy(quality = quality.toCommonTier())
                    ?.let { crossProviderFallback?.resolve(it) }
                    ?: throw error
                MusicQualityRuntime.recordActual(
                    songId = songId,
                    requested = quality,
                    actual = fallback.actualQuality.toMusicQuality(quality),
                )
                if (quality == MusicQualityRuntime.selected) {
                    CrossProviderPlaybackRuntime.record(songId, fallback.source)
                }
                ResolvedRequest(
                    uri = Uri.parse(fallback.url),
                    headers = fallback.requestHeaders,
                    expiresAtEpochMs = fallback.expiresAtEpochMs,
                    cacheIdentity = "${fallback.source.storageValue}:${fallback.resourceId}",
                )
            }
            synchronized(cacheLock) { resolvedUris[key] = resolved }
            pending.complete(resolved)
            resolved
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
        val fallbackRequest = fallbackRequest(uri, songId, requestedQuality)
        val key = resolveKey(songId, requestedQuality, currentCookieHeader, fallbackRequest)

        val resolved = resolveSongRequest(songId, requestedQuality, fallbackRequest)
        val cacheKey = playbackCacheKey(
            songId = songId,
            quality = requestedQuality,
            cookieHeader = currentCookieHeader,
            sourceIdentity = "${resolved.cacheIdentity}:${crossProviderFallback?.cacheIdentity().orEmpty()}",
        )

        return dataSpec.buildUpon()
            .setUri(resolved.uri)
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + resolved.headers)
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
        val fallbackRequest = fallbackRequest(uri, songId, requestedQuality)
        return cached(resolveKey(songId, requestedQuality, currentCookieHeader, fallbackRequest))?.uri ?: uri
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
        resolveSongRequest(songId, quality, fallbackRequest(uri, songId, quality))
    }

    private fun resolveKey(
        songId: Long,
        quality: MusicQuality,
        cookieHeader: String,
        fallbackRequest: CrossProviderFallbackRequest?,
    ) = ResolveKey(
        songId = songId,
        quality = quality,
        cookieHeader = cookieHeader,
        fallbackIdentity = crossProviderFallback?.cacheIdentity().orEmpty(),
        metadataIdentity = fallbackRequest?.let {
            "${it.title}\u001f${it.artist}\u001f${it.durationMs.orZero()}"
        }.orEmpty(),
    )

    private fun fallbackRequest(
        uri: Uri,
        songId: Long,
        quality: MusicQuality,
    ): CrossProviderFallbackRequest? {
        val title = uri.getQueryParameter(TITLE_QUERY)?.takeIf(String::isNotBlank) ?: return null
        val artist = uri.getQueryParameter(ARTIST_QUERY)?.takeIf(String::isNotBlank) ?: return null
        return CrossProviderFallbackRequest(
            songId = songId,
            title = title,
            artist = artist,
            durationMs = uri.getQueryParameter(DURATION_QUERY)?.toLongOrNull()?.takeIf { it > 0L },
            quality = quality.toCommonTier(),
        )
    }

    private fun cached(key: ResolveKey): ResolvedRequest? = synchronized(cacheLock) {
        resolvedUris[key]?.also { cached ->
            if (cached.expiresAtEpochMs?.let { System.currentTimeMillis() >= it } == true) {
                resolvedUris.remove(key)
                return@synchronized null
            }
        }
    }

    private fun providerDelegate(): ProviderPlaybackResolver? {
        providerDelegate?.let { return it }
        val registry = ProviderPlaybackRuntime.registryOrNull() ?: return null
        return synchronized(this) {
            providerDelegate ?: ProviderPlaybackResolver(
                neteaseResolver = this,
                providers = registry,
                authKeyProvider = ProviderPlaybackRuntime::authKey,
                providerPlaybackEnabled = providerPlaybackEnabled,
            ).also { providerDelegate = it }
        }
    }

    companion object {
        private const val MELOX_SCHEME = "melox"
        private const val SONG_HOST = "song"
        private const val QUALITY_QUERY = "quality"
        private const val TITLE_QUERY = "title"
        private const val ARTIST_QUERY = "artist"
        private const val DURATION_QUERY = "durationMs"
        private const val MAX_RESOLVED_URIS = 96
        private const val PLAYBACK_CACHE_VERSION = 3

        private fun playbackCacheKey(
            songId: Long,
            quality: MusicQuality,
            cookieHeader: String,
            sourceIdentity: String,
        ): String =
            "netease:v$PLAYBACK_CACHE_VERSION:$songId:${quality.apiLevel}:" +
                "${cookieHeader.hashCode().toUInt().toString(16)}:${sourceIdentity.hashCode().toUInt().toString(16)}"

        fun uriForSong(
            songId: Long,
            quality: MusicQuality = MusicQualityRuntime.selected,
            title: String? = null,
            artist: String? = null,
            durationMs: Long? = null,
        ): Uri = Uri.Builder()
            .scheme(MELOX_SCHEME)
            .authority(SONG_HOST)
            .appendPath(songId.toString())
            .appendQueryParameter(QUALITY_QUERY, quality.apiLevel)
            .apply {
                title?.takeIf(String::isNotBlank)?.let { appendQueryParameter(TITLE_QUERY, it) }
                artist?.takeIf(String::isNotBlank)?.let { appendQueryParameter(ARTIST_QUERY, it) }
                durationMs?.takeIf { it > 0L }?.let { appendQueryParameter(DURATION_QUERY, it.toString()) }
            }
            .build()
    }
}

private fun Long?.orZero(): Long = this ?: 0L

private fun AudioQualityTier.toMusicQuality(requested: MusicQuality): MusicQuality = when (this) {
    AudioQualityTier.Standard -> MusicQuality.Standard
    AudioQualityTier.High -> MusicQuality.High
    AudioQualityTier.Lossless -> MusicQuality.Lossless
    AudioQualityTier.HiResolution -> MusicQuality.HiResolution
    AudioQualityTier.Immersive -> requested.takeIf {
        it == MusicQuality.HighDefinitionSurround || it == MusicQuality.ImmersiveSurround
    } ?: MusicQuality.ImmersiveSurround
    AudioQualityTier.Master -> MusicQuality.UltraClearMaster
}
