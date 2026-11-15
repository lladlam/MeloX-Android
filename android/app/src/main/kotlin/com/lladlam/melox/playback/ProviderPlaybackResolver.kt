package com.lladlam.melox.playback


import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.lladlam.melox.core.audio.MeloXPlaybackUnavailableException
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.model.SourceId
import com.lladlam.melox.core.plugin.PluginMusicProvider
import com.lladlam.melox.core.music.provider.MusicProviderRegistry
import com.lladlam.melox.core.music.provider.PlaybackCapability
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/** Hard bound for a single provider resolution attempt. */
private const val PROVIDER_RESOLVE_TIMEOUT_MS = 20_000L

/**
 * Adds provider-aware `melox://track/...` URIs while delegating every legacy
 * `melox://song/<Long>` URI to the untouched NetEase resolver.
 */
@OptIn(UnstableApi::class)
class ProviderPlaybackResolver(
    private val neteaseResolver: NeteasePlaybackResolver,
    private val providers: MusicProviderRegistry,
    private val localSourceProvider: (MusicResourceId) -> Uri? = { null },
    private val authKeyProvider: (MusicSource) -> String = { "" },
    private val providerPlaybackEnabled: (MusicSource) -> Boolean = { true },
    private val thirdPartySourcesEnabled: () -> Boolean = { true },
    private val crossProviderFallback: CrossProviderPlaybackFallbackResolver? = null,
    private val thirdPartyResolver: ThirdPartyPlaybackResolver? = null,
) : ResolvingDataSource.Resolver {
    private data class ResolveKey(
        val requestUri: String,
        val authKey: String,
        val quality: AudioQualityTier,
        val fallbackIdentity: String,
    )
    private data class ResolvedRequest(
        val uri: Uri,
        val headers: Map<String, String>,
        val expiresAtEpochMs: Long? = null,
    )

    private val cacheLock = Any()
    private val resolvedUris = object : LinkedHashMap<ResolveKey, ResolvedRequest>(MAX_RESOLVED_URIS, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ResolveKey, ResolvedRequest>?): Boolean =
            size > MAX_RESOLVED_URIS
    }
    private val inFlight = ConcurrentHashMap<ResolveKey, CompletableFuture<ResolvedRequest>>()

    private val fallbackChain = PlaybackFallbackChain(crossProviderFallback, thirdPartyResolver)

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != MeloXScheme) return dataSpec
        if (uri.host == LegacySongHost) return neteaseResolver.resolveDataSpec(dataSpec)
        if (uri.host != ProviderTrackHost) return dataSpec
        val dynamicSource = uri.pathSegments.firstOrNull()?.let(SourceId::parse)?.takeIf { it.builtIn == null }
        if (dynamicSource != null) return resolveDynamicRequest(dataSpec, dynamicSource)
        val resolved = resolveProviderRequest(uri)
        return dataSpec.buildUpon()
            .setUri(resolved.uri)
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + resolved.headers)
            // Provider CDN URLs are short-lived. Do not reuse bytes cached for an earlier resolution.
            .setKey(resolved.uri.toString())
            .build()
    }

    private fun resolveDynamicRequest(dataSpec: DataSpec, source: SourceId, expected: PluginMusicProvider? = null): DataSpec {
        val uri = dataSpec.uri
        val value = uri.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() }
            ?: throw IOException("Invalid plugin resource ID")
        // A downloaded plugin track has to play from the local file, exactly
        // like the built-in sources do. The local copy wins and the plugin is
        // only asked when there is no local file (otherwise the download is
        // useless and offline playback of a plugin track fails).
        if (expected == null) {
            localSourceProvider(MusicResourceId(source, value))?.let { local ->
                return dataSpec.buildUpon().setUri(local).setKey(local.toString()).build()
            }
        }
        if (providers[source] == null) providers.awaitPluginRestore()
        val generation = providers.generation
        val provider = providers[source] as? PluginMusicProvider
            ?: throw IOException("Music source ${source.value} is missing or disabled")
        if (expected != null && provider !== expected) throw IOException("Plugin account or package changed before retry")
        if (!provider.supports(com.lladlam.melox.core.music.provider.MusicCapability.Playback))
            throw IOException("Plugin does not support playback")
        val restored = uri.getQueryParameter("standardTrack")?.let { raw ->
            runCatching {
                com.lladlam.melox.core.plugin.PluginJson.check(raw)
                com.lladlam.melox.core.music.model.MeloXTrackJson.decode(org.json.JSONObject(raw))
            }.getOrNull()?.takeIf { it.id == MusicResourceId(source, value) }
        }
        val track = restored ?: MusicTrack(MusicResourceId(source, value), uri.getQueryParameter(TrackTitleQuery).orEmpty(),
            uri.getQueryParameter(TrackArtistsQuery).orEmpty().split('\u001f').filter { it.isNotBlank() }
                .map { com.lladlam.melox.core.music.model.MusicArtistRef(name = it) },
            durationMs = uri.getQueryParameter(TrackDurationQuery)?.toLongOrNull())
        val resolution = runBlocking(Dispatchers.IO) {
            kotlinx.coroutines.withTimeout(PROVIDER_RESOLVE_TIMEOUT_MS) {
                provider.resolvePlayback(track, currentQuality(uri))
            }
        }
        if (providers.generation != generation || providers[source] !== provider) throw IOException("Plugin changed during resolution")
        val (url, headers) = when (resolution) {
            is PlaybackResolution.Playable -> resolution.url to resolution.requestHeaders
            is PlaybackResolution.Preview -> resolution.url to emptyMap()
            else -> throw IOException("Plugin audio unavailable: ${resolution.javaClass.simpleName}")
        }
        return dataSpec.buildUpon().setUri(url).setHttpRequestHeaders(headers)
            .setCustomData(PluginMediaRequest(provider, providers, generation, (resolution as? PlaybackResolution.Playable)?.credentialReference == "active",
                reResolve = { resolveDynamicRequest(dataSpec, source, provider) }))
            .setKey("plugin:${source.value}:$generation:$url").build()
    }

    override fun resolveReportedUri(uri: Uri): Uri {
        if (uri.scheme != MeloXScheme) return uri
        if (uri.host == LegacySongHost) return neteaseResolver.resolveReportedUri(uri)
        if (uri.host != ProviderTrackHost) return uri
        val source = parseSource(uri) ?: return uri
        val quality = currentQuality(uri)
        return cached(ResolveKey(uri.toString(), authKeyProvider(source), quality, fallbackIdentity()))?.uri ?: uri
    }

    internal fun prefetch(uri: Uri) {
        if (uri.pathSegments.firstOrNull()?.let(SourceId::parse)?.builtIn == null) return
        if (isProviderTrackUri(uri)) resolveProviderRequest(uri)
    }

    private fun resolveProviderRequest(uri: Uri): ResolvedRequest {
        val source = parseSource(uri)
            ?: throw IOException("Invalid MeloX provider source: $uri")
        if (!providerPlaybackEnabled(source)) {
            throw IOException("${source.displayName} 播放接口已由远程兼容性配置临时关闭")
        }
        val resourceValue = uri.pathSegments.getOrNull(1)
            ?.let(Uri::decode)
            ?.takeIf(String::isNotBlank)
            ?: throw IOException("Invalid MeloX provider track ID: $uri")
        val quality = currentQuality(uri)
        val key = ResolveKey(uri.toString(), authKeyProvider(source), quality, fallbackIdentity())
        cached(key)?.let { return it }
        val pending = CompletableFuture<ResolvedRequest>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return runCatching {
            // A provider that never completes must not block the resolver
            // thread forever; the waiting caller gets a bounded failure.
            existing.get(PROVIDER_RESOLVE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        }.getOrElse { error ->
            inFlight.remove(key, existing)
            throw IOException("Unable to resolve provider playback source", error.cause ?: error)
        }

        return try {
            val id = MusicResourceId(source, resourceValue)
            val track = MusicTrack(
                id = id,
                title = uri.getQueryParameter(TrackTitleQuery).orEmpty(),
                artists = uri.getQueryParameter(TrackArtistsQuery).orEmpty().split('\u001f')
                    .filter(String::isNotBlank)
                    .map { com.lladlam.melox.core.music.model.MusicArtistRef(name = it) },
                durationMs = uri.getQueryParameter(TrackDurationQuery)?.toLongOrNull(),
                providerMetadata = providerMetadata(uri, id),
            )
            localSourceProvider(id)?.let { local ->
                val result = ResolvedRequest(local, emptyMap())
                synchronized(cacheLock) { resolvedUris[key] = result }
                pending.complete(result)
                return result
            }
            Log.d(TAG, "resolve detail source=${source.storageValue} id=${resourceValue.take(8)} title=${track.title.take(40)} " +
                "artists=${track.artistText.take(60)} durationMs=${track.durationMs} metadata=${track.providerMetadata.javaClass.simpleName}")
            Log.i(TAG, "Resolve start source=${source.storageValue} quality=${quality.name} thirdParty=${thirdPartySourcesEnabled()}")
            val provider = providers.require(source)
            val playback = provider as? PlaybackCapability
                ?: throw IOException("${provider.displayName} 当前没有实现播放能力")
            val resolution = runBlocking(Dispatchers.IO) {
                kotlinx.coroutines.withTimeout(PROVIDER_RESOLVE_TIMEOUT_MS) {
                    builtInPlaybackResult(thirdPartySourcesEnabled()) { playback.resolvePlayback(track, quality) }
                }
            }

            /**
             * Playback order: current provider → strict full-audio match from
             * another built-in source → user-configured third-party resolvers.
             *
             * The cross-provider stage only runs when the user enabled
             * "fetch unavailable resources from other platforms"; the final
             * stage only runs when a third-party source is configured and its
             * consent switch is on.
             */
            fun fallbackOrThrow(
                track: MusicTrack,
                quality: AudioQualityTier,
                message: String,
                membershipFailure: Boolean,
            ): ResolvedRequest {
                val fallback = runCatching {
                    fallbackChain.resolve(track, quality, membershipFailure)
                }.onFailure { Log.w(TAG, "Playback fallback failed", it) }
                    .getOrNull()
                if (fallback != null) {
                    fallback.crossProvider?.let { cross ->
                        ProviderPlaybackQualityRuntime.recordActual(
                            id = id,
                            requested = quality,
                            actual = cross.actualQuality,
                        )
                    }
                    fallback.thirdParty?.actualQuality?.let { actual ->
                        ProviderPlaybackQualityRuntime.recordActual(
                            id = id,
                            requested = quality,
                            actual = actual,
                        )
                    }
                    return ResolvedRequest(
                        uri = Uri.parse(fallback.url),
                        headers = fallback.requestHeaders,
                        expiresAtEpochMs = fallback.expiresAtEpochMs,
                    )
                }
                throw MeloXPlaybackUnavailableException(message)
            }
            val result = when (resolution) {
                is PlaybackResolution.Playable -> {
                    ProviderPlaybackQualityRuntime.recordActual(
                        id = id,
                        requested = quality,
                        actual = resolution.actualQuality ?: resolution.requestedQuality,
                    )
                    ResolvedRequest(Uri.parse(resolution.url), resolution.requestHeaders, resolution.expiresAtEpochMs)
                }
                is PlaybackResolution.Preview -> ResolvedRequest(Uri.parse(resolution.url), emptyMap())
                // ⚠ 以下「拿不到链接」的分支必须用 `MeloXPlaybackUnavailableException`
                //   而不是普通 `IOException`（2026-09-28 修「切到无音源的歌会弹回原曲」）：
                //   服务端的 `onPlayerError` 靠这个类型把它和「网络抖动」区分开，
                //   从而**向前跳过**而不是卡在错误态等一个永远不会来的重试。
                //   注意 `LoginRequired` **不**归这一类 —— 登录态是全局的，一旦缺失
                //   整队都播不了，逐个跳过只会把整个队列清空，应该保留现曲目等人处理。
                PlaybackResolution.LoginRequired -> throw IOException("${provider.displayName} 需要登录后播放")
                PlaybackResolution.SubscriptionRequired -> fallbackOrThrow(
                    track, quality, "${provider.displayName} 当前歌曲需要对应会员权益", membershipFailure = true,
                )
                PlaybackResolution.RegionRestricted -> fallbackOrThrow(
                    track, quality, "${provider.displayName} 当前地区不可播放", membershipFailure = true,
                )
                PlaybackResolution.CopyrightRestricted -> fallbackOrThrow(
                    track, quality, "${provider.displayName} 当前版权不可播放", membershipFailure = true,
                )
                is PlaybackResolution.Unavailable -> fallbackOrThrow(
                    track, quality,
                    resolution.reason ?: "${provider.displayName} 暂时没有可播放音源",
                    membershipFailure = false,
                )
            }
            synchronized(cacheLock) { resolvedUris[key] = result }
            pending.complete(result)
            result
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    private fun cached(key: ResolveKey): ResolvedRequest? = synchronized(cacheLock) {
        resolvedUris[key]?.also { cached ->
            if (cached.expiresAtEpochMs?.let { System.currentTimeMillis() >= it } == true) {
                resolvedUris.remove(key)
                return@synchronized null
            }
        }
    }

    /** Settings that change the fallback chain must invalidate resolved URLs too. */
    private fun fallbackIdentity(): String = fallbackChain.cacheIdentity()

    private fun currentQuality(uri: Uri): AudioQualityTier {
        // The quality selector updates this runtime before Media3 prepare(). That
        // allows a stable provider media identity to request a fresh VKey at the
        // newly selected tier instead of reusing its initial query parameter.
        val runtime = MusicQualityRuntime.selected.toCommonTier()
        return runtime.takeIf { MusicQualityRuntime.selected.apiLevel.isNotBlank() }
            ?: uri.getQueryParameter(QualityQuery)
                ?.let { raw -> AudioQualityTier.entries.firstOrNull { it.name == raw } }
            ?: AudioQualityTier.Standard
    }

    private fun parseSource(uri: Uri): MusicSource? {
        val raw = uri.pathSegments.firstOrNull() ?: return null
        return MusicSource.entries.firstOrNull { it.storageValue == raw }
    }

    private fun providerMetadata(uri: Uri, id: MusicResourceId): ProviderTrackMetadata = when (id.source) {
        MusicSource.Netease -> ProviderTrackMetadata.Netease(
            numericId = id.value.toLongOrNull()
                ?: throw IOException("Invalid NetEase track ID: ${id.value}"),
        )
        MusicSource.QQMusic -> ProviderTrackMetadata.QQMusic(
            songMid = id.value,
            mediaMid = uri.getQueryParameter(QQMediaMidQuery)?.takeIf(String::isNotBlank),
            numericSongId = uri.getQueryParameter(QQNumericIdQuery)?.toLongOrNull(),
        )
        MusicSource.Kugou,
        MusicSource.KugouLite,
        -> ProviderTrackMetadata.Kugou(
            hash = id.value,
            albumAudioId = uri.getQueryParameter(KugouAlbumAudioIdQuery)?.toLongOrNull(),
            albumId = uri.getQueryParameter(KugouAlbumIdQuery)?.takeIf(String::isNotBlank),
        )
        MusicSource.Kuwo -> ProviderTrackMetadata.Kuwo(
            mid = id.value.toLongOrNull()
                ?: throw IOException("Invalid Kuwo track ID: ${id.value}"),
        )
        MusicSource.AppleMusic -> ProviderTrackMetadata.AppleMusic(
            catalogId = id.value,
            storefront = uri.getQueryParameter(AppleStorefrontQuery).orEmpty().ifBlank { "us" },
            previewUrl = uri.getQueryParameter(ApplePreviewUrlQuery)?.takeIf(String::isNotBlank),
        )
        MusicSource.Bilibili -> {
            val (bvid, cid) = com.lladlam.melox.core.provider.bilibili.BilibiliProvider.parseIdentity(id.value)
                ?: throw IOException("Invalid Bilibili track ID")
            ProviderTrackMetadata.Bilibili(bvid, cid)
        }
        MusicSource.Spotify -> ProviderTrackMetadata.Spotify(
            id.value,
            uri.getQueryParameter(SpotifyIsrcQuery)?.takeIf(String::isNotBlank),
        )
        MusicSource.YouTubeMusic -> ProviderTrackMetadata.Empty
        MusicSource.Qishui -> ProviderTrackMetadata.Empty
        MusicSource.Jellyfin -> ProviderTrackMetadata.Empty
        MusicSource.Local -> ProviderTrackMetadata.Local(
            contentUri = uri.getQueryParameter("localContentUri").orEmpty(),
            fileKey = id.value,
        )
    }

    companion object {
        private const val MeloXScheme = "melox"
        private const val LegacySongHost = "song"
        private const val ProviderTrackHost = "track"
        private const val QualityQuery = "qualityTier"
        private const val QQMediaMidQuery = "qqMediaMid"
        private const val QQNumericIdQuery = "qqNumericId"
        private const val KugouAlbumAudioIdQuery = "kgAlbumAudioId"
        private const val KugouAlbumIdQuery = "kgAlbumId"
        private const val AppleStorefrontQuery = "appleStorefront"
        private const val ApplePreviewUrlQuery = "applePreviewUrl"
        private const val SpotifyTitleQuery = "spotifyTitle"
        private const val SpotifyArtistsQuery = "spotifyArtists"
        private const val SpotifyDurationQuery = "spotifyDurationMs"
        private const val SpotifyIsrcQuery = "spotifyIsrc"
        private const val TrackTitleQuery = "trackTitle"
        private const val TrackArtistsQuery = "trackArtists"
        private const val TrackDurationQuery = "trackDurationMs"
        private const val TAG = "MeloXThirdParty"
        private const val MAX_RESOLVED_URIS = 96

        fun isProviderTrackUri(uri: Uri): Boolean =
            uri.scheme == MeloXScheme && uri.host == ProviderTrackHost

        fun uriForTrack(
            track: MusicTrack,
            quality: AudioQualityTier,
        ): Uri = Uri.Builder()
            .scheme(MeloXScheme)
            .authority(ProviderTrackHost)
            .appendPath(track.id.sourceId.value)
            .appendPath(track.id.value)
            .appendQueryParameter(QualityQuery, quality.name)
            .appendQueryParameter(TrackTitleQuery, track.title)
            .appendQueryParameter(TrackArtistsQuery, track.artists.joinToString("\u001f") { it.name })
            .apply { track.durationMs?.let { appendQueryParameter(TrackDurationQuery, it.toString()) } }
            .apply { if (track.id.sourceId.builtIn == null) appendQueryParameter("standardTrack",
                com.lladlam.melox.core.music.model.MeloXTrackJson.encode(track).toString()) }
            .apply {
                when (val metadata = track.providerMetadata) {
                    is ProviderTrackMetadata.QQMusic -> {
                        metadata.mediaMid?.takeIf(String::isNotBlank)?.let {
                            appendQueryParameter(QQMediaMidQuery, it)
                        }
                        metadata.numericSongId?.let {
                            appendQueryParameter(QQNumericIdQuery, it.toString())
                        }
                    }
                    is ProviderTrackMetadata.Kugou -> {
                        metadata.albumAudioId?.let {
                            appendQueryParameter(KugouAlbumAudioIdQuery, it.toString())
                        }
                        metadata.albumId?.takeIf(String::isNotBlank)?.let {
                            appendQueryParameter(KugouAlbumIdQuery, it)
                        }
                    }
                    is ProviderTrackMetadata.AppleMusic -> {
                        appendQueryParameter(AppleStorefrontQuery, metadata.storefront)
                        metadata.previewUrl?.takeIf(String::isNotBlank)?.let {
                            appendQueryParameter(ApplePreviewUrlQuery, it)
                        }
                    }
                    is ProviderTrackMetadata.Bilibili -> Unit
                    is ProviderTrackMetadata.Spotify -> {
                        appendQueryParameter(SpotifyTitleQuery, track.title)
                        appendQueryParameter(
                            SpotifyArtistsQuery,
                            track.artists.joinToString("\u001f") { it.name },
                        )
                        track.durationMs?.let { appendQueryParameter(SpotifyDurationQuery, it.toString()) }
                        metadata.isrc?.takeIf(String::isNotBlank)?.let {
                            appendQueryParameter(SpotifyIsrcQuery, it)
                        }
                    }
                    is ProviderTrackMetadata.Local -> {
                        appendQueryParameter("localContentUri", metadata.contentUri)
                    }
                    else -> Unit
                }
            }
            .build()
    }
}
