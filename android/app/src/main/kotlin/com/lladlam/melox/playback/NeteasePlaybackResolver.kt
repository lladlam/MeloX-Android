package com.lladlam.melox.playback

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.audio.MeloXPlaybackUnavailableException
import com.lladlam.melox.core.audio.NeteaseQualityClient
import com.lladlam.melox.core.audio.NeteasePlaybackUnavailableException
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.network.NeteaseSearchClient
import com.lladlam.melox.core.music.provider.PlaybackAccountStore
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class NeteasePlaybackResolver(
    private val cookieProvider: () -> String = { "" },
    @Suppress("UNUSED_PARAMETER")
    private val client: NeteaseSearchClient = NeteaseSearchClient(cookieProvider = cookieProvider),
    private val localSourceProvider: (Long) -> Uri? = { null },
    private val providerLocalSourceProvider: (MusicResourceId) -> Uri? = { null },
    private val crossProviderFallback: CrossProviderPlaybackFallbackResolver? = null,
    private val providerPlaybackEnabled: (com.lladlam.melox.core.music.model.MusicSource) -> Boolean = { true },
    private val thirdPartySourcesEnabled: () -> Boolean = { true },
    private val thirdPartyResolver: ThirdPartyPlaybackResolver? = null,
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
    private data class RecentFailure(val error: Throwable, val atMs: Long)

    private val cacheLock = Any()
    private val resolvedUris = object : LinkedHashMap<ResolveKey, ResolvedRequest>(MAX_RESOLVED_URIS, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ResolveKey, ResolvedRequest>?): Boolean =
            size > MAX_RESOLVED_URIS
    }
    private val inFlight = ConcurrentHashMap<ResolveKey, CompletableFuture<ResolvedRequest>>()

    /**
     * 「音源不可用」短期冷却表。
     *
     * ExoPlayer 在数据源解析失败后会**反复重试**，而每次重试都会重跑整条降级链
     * （官方 → 跨平台兜底，含多处十几秒超时）。2026-09-30 真机实测：
     * 同一首歌 22 秒内跑了两轮完整兜底，播放器一直停在 `STATE_BUFFERING`，
     * 用户看到的就是「进度条一直转、没有任何提示」。
     *
     * 把这类失败记住 [FAILURE_COOLDOWN_MS]，重试时立刻抛出，让 `onPlayerError`
     * 尽快触发（从而给出跳过/提示），而不是让用户干等。
     * 只记「音源不可用」语义的异常；网络抖动等仍走正常重试。
     */
    private val recentFailures = object : LinkedHashMap<ResolveKey, RecentFailure>(MAX_RESOLVED_URIS, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ResolveKey, RecentFailure>?): Boolean =
            size > MAX_RESOLVED_URIS
    }

    private val qualityClient = NeteaseQualityClient(cookieProvider = cookieProvider)

    private val fallbackChain = PlaybackFallbackChain(crossProviderFallback, thirdPartyResolver)

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
            fallbackIdentity = fallbackChain.cacheIdentity(),
            metadataIdentity = fallbackRequest?.let {
                "${it.title}\u001f${it.artist}\u001f${it.durationMs.orZero()}"
            }.orEmpty(),
        )
        cached(key)?.let { return it }
        recentFailure(key)?.let { throw it }
        val pending = CompletableFuture<ResolvedRequest>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) {
            if (existing.isCompletedExceptionally) {
                inFlight.remove(key, existing)
            } else {
                return runCatching { existing.get(20L, TimeUnit.SECONDS) }
                    .getOrElse { throw IOException("Unable to resolve playback source", it.cause ?: it) }
            }
        }
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
                val track = fallbackTrack(songId, fallbackRequest)
                // The terminal NetEase failure covers membership/rights/region,
                // so it counts as a membership failure for the third-party switch.
                val fallback = track?.let {
                    fallbackChain.resolve(it, quality.toCommonTier(), membershipFailure = true)
                } ?: throw error
                fallback.crossProvider?.let { cross ->
                    MusicQualityRuntime.recordActual(
                        songId = songId,
                        requested = quality,
                        actual = cross.actualQuality.toMusicQuality(quality),
                    )
                    if (quality == MusicQualityRuntime.selected) {
                        CrossProviderPlaybackRuntime.record(songId, cross.source)
                    }
                }
                ResolvedRequest(
                    uri = Uri.parse(fallback.url),
                    headers = fallback.requestHeaders,
                    expiresAtEpochMs = fallback.expiresAtEpochMs,
                    cacheIdentity = fallback.cacheIdentity,
                )
            }
            synchronized(cacheLock) {
                recentFailures.remove(key)
                resolvedUris[key] = resolved
            }
            pending.complete(resolved)
            resolved
        } catch (error: Throwable) {
            if (error.blocksPlaybackSource()) {
                synchronized(cacheLock) {
                    recentFailures[key] = RecentFailure(error, System.currentTimeMillis())
                }
            }
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
        fallbackIdentity = fallbackChain.cacheIdentity(),
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

    /**
     * Third-party stage for a legacy NetEase song. CHKSZ resolves by song ID;
     * LX scripts use their own source selection and may also match by metadata.
     */
    private fun fallbackTrack(
        songId: Long,
        request: CrossProviderFallbackRequest?,
    ): MusicTrack? {
        return MusicTrack(
            id = MusicResourceId(MusicSource.Netease, songId.toString()),
            title = request?.title.orEmpty(),
            artists = request?.artist.orEmpty()
                .let(CrossProviderPlaybackFallbackResolver::splitArtists)
                .map { MusicArtistRef(name = it) },
            durationMs = request?.durationMs,
            providerMetadata = ProviderTrackMetadata.Netease(songId),
        )
    }

    /** 命中冷却期内的失败则原样抛出；过期则清除。 */
    private fun recentFailure(key: ResolveKey): Throwable? = synchronized(cacheLock) {
        val recent = recentFailures[key] ?: return@synchronized null
        if (System.currentTimeMillis() - recent.atMs > FAILURE_COOLDOWN_MS) {
            recentFailures.remove(key)
            return@synchronized null
        }
        Log.i(TAG, "Resolve skipped (recent source failure) song=${key.songId} quality=${key.quality}")
        recent.error
    }

    private fun providerDelegate(): ProviderPlaybackResolver? {
        providerDelegate?.let { return it }
        val registry = ProviderPlaybackRuntime.registryOrNull() ?: return null
        return synchronized(this) {
            providerDelegate ?: ProviderPlaybackResolver(
                neteaseResolver = this,
                providers = registry,
                localSourceProvider = providerLocalSourceProvider,
                authKeyProvider = ProviderPlaybackRuntime::authKey,
                providerPlaybackEnabled = providerPlaybackEnabled,
                thirdPartySourcesEnabled = thirdPartySourcesEnabled,
                crossProviderFallback = crossProviderFallback,
                thirdPartyResolver = thirdPartyResolver,
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
        private const val TAG = "MeloXNeteaseResolver"
        private const val MAX_RESOLVED_URIS = 96
        private const val PLAYBACK_CACHE_VERSION = 3

        /** 「音源不可用」失败的冷却时长：重试期间不重复跑整条降级链。 */
        private const val FAILURE_COOLDOWN_MS = 15_000L

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

/**
 * 该异常是否表示「音源本身不可用」（网易云无版权/会员/地区限制，或降级链也没拿到链接），
 * 而不是网络抖动。只有这类失败才值得记入冷却表。
 */
private fun Throwable.blocksPlaybackSource(): Boolean {
    var cursor: Throwable? = this
    var depth = 0
    while (cursor != null && depth++ < 8) {
        if (cursor is NeteasePlaybackUnavailableException || cursor is MeloXPlaybackUnavailableException) return true
        cursor = cursor.cause
    }
    return false
}

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
