package com.lladlam.melox.playback

import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.provider.MusicProviderRegistry
import com.lladlam.melox.core.music.provider.PlaybackCapability
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Adds provider-aware `melox://track/...` URIs while delegating every legacy
 * `melox://song/<Long>` URI to the untouched NetEase resolver.
 */
@OptIn(UnstableApi::class)
class ProviderPlaybackResolver(
    private val neteaseResolver: NeteasePlaybackResolver,
    private val providers: MusicProviderRegistry,
    private val authKeyProvider: (MusicSource) -> String = { "" },
    private val providerPlaybackEnabled: (MusicSource) -> Boolean = { true },
    private val chkszPlayback: ChkszPlaybackResolver? = null,
    private val lxUserPlayback: LxUserPlaybackResolver? = null,
    private val thirdPartySourcesEnabled: () -> Boolean = { true },
    private val thirdPartyOnlyForMembership: () -> Boolean = { false },
) : ResolvingDataSource.Resolver {
    private data class ResolveKey(
        val requestUri: String,
        val authKey: String,
        val quality: AudioQualityTier,
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

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val uri = dataSpec.uri
        if (uri.scheme != MeloXScheme) return dataSpec
        if (uri.host == LegacySongHost) return neteaseResolver.resolveDataSpec(dataSpec)
        if (uri.host != ProviderTrackHost) return dataSpec
        val resolved = resolveProviderRequest(uri)
        return dataSpec.buildUpon()
            .setUri(resolved.uri)
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + resolved.headers)
            // Provider CDN URLs are short-lived. Do not reuse bytes cached for an earlier resolution.
            .setKey(resolved.uri.toString())
            .build()
    }

    override fun resolveReportedUri(uri: Uri): Uri {
        if (uri.scheme != MeloXScheme) return uri
        if (uri.host == LegacySongHost) return neteaseResolver.resolveReportedUri(uri)
        if (uri.host != ProviderTrackHost) return uri
        val source = parseSource(uri) ?: return uri
        val quality = currentQuality(uri)
        return cached(ResolveKey(uri.toString(), authKeyProvider(source), quality))?.uri ?: uri
    }

    internal fun prefetch(uri: Uri) {
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
        val key = ResolveKey(uri.toString(), authKeyProvider(source), quality)
        cached(key)?.let { return it }
        val pending = CompletableFuture<ResolvedRequest>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return runCatching { existing.get() }
            .getOrElse { throw IOException("Unable to resolve provider playback source", it.cause ?: it) }

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
            Log.d(TAG, "resolve detail source=${source.storageValue} id=${resourceValue.take(8)} title=${track.title.take(40)} " +
                "artists=${track.artistText.take(60)} durationMs=${track.durationMs} metadata=${track.providerMetadata.javaClass.simpleName}")
            Log.i(TAG, "Resolve start source=${source.storageValue} quality=${quality.name} thirdParty=${thirdPartySourcesEnabled()}")
            val allowExternalResolver = source != MusicSource.Jellyfin
            val lx = if (allowExternalResolver && thirdPartySourcesEnabled() && !thirdPartyOnlyForMembership()) {
                runCatching { lxUserPlayback?.resolve(track, quality) }
                    .onFailure { Log.w(TAG, "LX stage failed source=${source.storageValue} error=${it.javaClass.simpleName}") }
                    .getOrNull()
            } else null
            if (lx != null) {
                Log.i(TAG, "Resolve success source=${source.storageValue} stage=lx script=${lx.sourceId}")
                val result = ResolvedRequest(Uri.parse(lx.url), lx.requestHeaders)
                synchronized(cacheLock) { resolvedUris[key] = result }
                pending.complete(result)
                return result
            }
            val thirdParty = if (allowExternalResolver && thirdPartySourcesEnabled() && !thirdPartyOnlyForMembership()) {
                runCatching { chkszPlayback?.resolve(track, quality) }
                    .onFailure { Log.w(TAG, "CHKSZ stage failed source=${source.storageValue} error=${it.javaClass.simpleName}") }
                    .getOrNull()
            } else null
            if (thirdParty != null) {
                Log.i(TAG, "Resolve success source=${source.storageValue} stage=chksz")
                val result = ResolvedRequest(Uri.parse(thirdParty.url), emptyMap())
                synchronized(cacheLock) { resolvedUris[key] = result }
                pending.complete(result)
                return result
            }
            Log.i(
                TAG,
                "Resolve fallback source=${source.storageValue} stage=provider chksz=${chkszPlayback?.cacheIdentity() ?: "unavailable"}",
            )
            val provider = providers.require(source)
            val playback = provider as? PlaybackCapability
                ?: throw IOException("${provider.displayName} 当前没有实现播放能力")
            val resolution = runBlocking(Dispatchers.IO) {
                playback.resolvePlayback(track, quality)
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
                PlaybackResolution.LoginRequired -> throw IOException("${provider.displayName} 需要登录后播放")
                PlaybackResolution.SubscriptionRequired -> {
                    if (allowExternalResolver && thirdPartySourcesEnabled()) {
                        resolveThirdParty(track, quality, source)
                            ?: throw IOException("${provider.displayName} 当前歌曲需要对应会员权益")
                    } else {
                        throw IOException("${provider.displayName} 当前歌曲需要对应会员权益")
                    }
                }
                PlaybackResolution.RegionRestricted -> throw IOException("${provider.displayName} 当前地区不可播放")
                PlaybackResolution.CopyrightRestricted -> throw IOException("${provider.displayName} 当前版权不可播放")
                is PlaybackResolution.Unavailable -> throw IOException(
                    resolution.reason ?: "${provider.displayName} 暂时没有可播放音源",
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

    private fun resolveThirdParty(
        track: MusicTrack,
        quality: AudioQualityTier,
        source: MusicSource,
    ): ResolvedRequest? {
        val lx = runCatching { lxUserPlayback?.resolve(track, quality) }
            .onFailure { Log.w(TAG, "LX membership fallback failed source=${source.storageValue}", it) }
            .getOrNull()
        if (lx != null) return ResolvedRequest(Uri.parse(lx.url), lx.requestHeaders)
        val chksz = runCatching { chkszPlayback?.resolve(track, quality) }
            .onFailure { Log.w(TAG, "CHKSZ membership fallback failed source=${source.storageValue}", it) }
            .getOrNull()
        return chksz?.let { ResolvedRequest(Uri.parse(it.url), emptyMap()) }
    }

    private fun cached(key: ResolveKey): ResolvedRequest? = synchronized(cacheLock) {
        resolvedUris[key]?.also { cached ->
            if (cached.expiresAtEpochMs?.let { System.currentTimeMillis() >= it } == true) {
                resolvedUris.remove(key)
                return@synchronized null
            }
        }
    }

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
        MusicSource.Kugou -> ProviderTrackMetadata.Kugou(
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
            .appendPath(track.id.source.storageValue)
            .appendPath(track.id.value)
            .appendQueryParameter(QualityQuery, quality.name)
            .appendQueryParameter(TrackTitleQuery, track.title)
            .appendQueryParameter(TrackArtistsQuery, track.artists.joinToString("\u001f") { it.name })
            .apply { track.durationMs?.let { appendQueryParameter(TrackDurationQuery, it.toString()) } }
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
