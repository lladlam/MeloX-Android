package com.lladlam.melox.playback

import android.content.Context
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.ProviderTrackMetadata
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.MusicProviderRegistry
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.music.provider.TrackAggregation
import com.lladlam.melox.core.provider.spotify.SpotifyTrackMatcher
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigDefaults
import com.lladlam.melox.core.remoteconfig.MeloXRemoteFallbackConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

object CrossProviderPlaybackPreferences {
    private const val PreferencesName = "melox_playback"
    private const val EnabledKey = "cross_provider_unavailable_fallback"

    fun enabled(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        .getBoolean(EnabledKey, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(EnabledKey, enabled)
            .apply()
    }
}

object CrossProviderPlaybackRuntime {
    private val sourceByNeteaseSong = ConcurrentHashMap<Long, MusicSource>()

    fun record(songId: Long, source: MusicSource) {
        sourceByNeteaseSong[songId] = source
    }

    fun sourceFor(songId: Long?): MusicSource? = songId?.let(sourceByNeteaseSong::get)

    fun clear(songId: Long? = null) {
        if (songId == null) sourceByNeteaseSong.clear() else sourceByNeteaseSong.remove(songId)
    }
}

internal data class CrossProviderFallbackRequest(
    val songId: Long,
    val title: String,
    val artist: String,
    val durationMs: Long?,
    val quality: AudioQualityTier,
    /** Source that just failed. It is skipped so we never retry the same provider. */
    val source: MusicSource = MusicSource.Netease,
    val resourceId: String = songId.toString(),
)

internal data class CrossProviderFallbackResult(
    val source: MusicSource,
    val resourceId: String,
    val url: String,
    val requestHeaders: Map<String, String>,
    val actualQuality: AudioQualityTier,
    val expiresAtEpochMs: Long?,
)

class CrossProviderPlaybackFallbackResolver(
    private val enabledProvider: () -> Boolean,
    private val registryProvider: () -> MusicProviderRegistry?,
    private val cacheIdentityProvider: () -> String = { "" },
    private val eventLogger: (String) -> Unit = {},
    private val fallbackConfigProvider: () -> MeloXRemoteFallbackConfig = {
        MeloXRemoteConfigDefaults.Config.fallback
    },
    /**
     * A single slow provider must not consume the whole fallback budget.
     * YouTube Music, for example, can take almost the complete 6 s budget to
     * fail; the next provider then resolves just as the outer timeout expires
     * and the successful URL is discarded.
     */
    private val perProviderTimeoutMs: Long = 2_000L,
) {
    fun cacheIdentity(): String = if (enabledProvider()) {
        val fallback = fallbackConfigProvider()
        "enabled:${fallback.order.joinToString(",")}:${fallback.disabledProviders.sorted().joinToString(",")}:" +
            "${fallback.timeoutMs}:${cacheIdentityProvider()}"
    } else {
        "disabled"
    }

    internal fun resolve(request: CrossProviderFallbackRequest): CrossProviderFallbackResult? {
        if (!enabledProvider() || request.title.isBlank() || request.artist.isBlank()) {
            eventLogger("skipped song=${request.songId}: disabled or missing metadata")
            return null
        }
        val fallbackConfig = fallbackConfigProvider()
        if (!fallbackConfig.enabled) {
            eventLogger("skipped song=${request.songId}: remotely disabled")
            return null
        }
        val order = fallbackConfig.order.withIndex().associate { (index, source) -> source to index }
        val providers = registryProvider()?.providers.orEmpty()
            .filter(::isEligibleFallbackProvider)
            .filterNot { it.source == request.source }
            .filterNot { it.source.storageValue in fallbackConfig.disabledProviders }
            .sortedBy { order[it.source.storageValue] ?: Int.MAX_VALUE }
        if (providers.isEmpty()) {
            eventLogger("skipped song=${request.songId}: no eligible providers")
            return null
        }

        val artists = splitArtists(request.artist)
        val primaryArtist = artists.firstOrNull() ?: return null
        val sourceTrack = MusicTrack(
            id = MusicResourceId(request.source, request.resourceId),
            title = request.title,
            artists = artists.map { MusicArtistRef(name = it) },
            durationMs = request.durationMs?.takeIf { it > 0L },
            availability = TrackAvailability.Unavailable,
            providerMetadata = providerMetadata(request),
        )
        val query = "${request.title} $primaryArtist"

        return runBlocking(Dispatchers.IO) {
            val resolved = withTimeoutOrNull(fallbackConfig.timeoutMs.toLong()) {
                // Sequential by design: the configured order is the priority.
                // Searching every provider in parallel would let a lower-priority
                // source win on a tie and would waste mobile data.
                val providerTimeout = perProviderTimeoutMs.coerceAtLeast(1L)
                for (provider in providers) {
                    val search = provider as SearchCapability
                    val candidates = try {
                        withTimeoutOrNull(providerTimeout) {
                            search.searchSongs(query, page = 1, pageSize = 10).items
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Throwable) {
                        eventLogger(
                            "search ${provider.source.storageValue} failed: ${errorLabel(error)}",
                        )
                        continue
                    }
                    if (candidates == null) {
                        eventLogger(
                            "search ${provider.source.storageValue} timed out after ${providerTimeout}ms",
                        )
                        continue
                    }
                    eventLogger("search ${provider.source.storageValue}: ${candidates.size} candidates")
                    val ranked: List<MusicTrack> = if (provider.source == MusicSource.Bilibili) {
                        // Bilibili titles are often uploader-formatted
                        // ("尽情享受吧！因为『今天不是明天』…"). Exact title
                        // matching rejects the official video, so use a
                        // contained-title plus uploader/duration gate.
                        rankBilibiliMatches(sourceTrack, candidates)
                    } else {
                        SpotifyTrackMatcher.rank(sourceTrack, candidates).let { matches ->
                            if (sourceTrack.durationMs != null) {
                                matches
                            } else {
                                // 没有时长时不能靠时长消歧，只接受歌手名单完全一致的结果。
                                matches.filter { artistSet(sourceTrack) == artistSet(it.candidate) }
                            }
                        }.map { it.candidate }
                    }
                    eventLogger("strict matches ${provider.source.storageValue} song=${request.songId}: ${ranked.size}")
                    for (match in ranked) {
                        val playback = provider as PlaybackCapability
                        val qualities = playbackQualityCandidates(request.quality)
                        var lastQuality: AudioQualityTier? = null
                        var lastResolution: PlaybackResolution? = null
                        val resolution = try {
                            // A provider may have no full URL at the requested
                            // lossless/master tier but still have a complete
                            // standard track. Try lower tiers before declaring
                            // the match unusable; previews are never accepted.
                            withTimeoutOrNull(providerTimeout * 2) {
                                for (candidateQuality in qualities) {
                                    lastQuality = candidateQuality
                                    val attempt = playback.resolvePlayback(match, candidateQuality)
                                    if (attempt is PlaybackResolution.Playable) {
                                        return@withTimeoutOrNull attempt
                                    }
                                    lastResolution = attempt
                                }
                                lastResolution
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            eventLogger(
                                "playback ${provider.source.storageValue} failed: ${errorLabel(error)}",
                            )
                            continue
                        }
                        if (resolution == null) {
                            eventLogger(
                                "playback ${provider.source.storageValue} timed out after ${providerTimeout * 2}ms",
                            )
                            continue
                        }
                        if (resolution is PlaybackResolution.Playable) {
                            eventLogger("resolved song=${request.songId} via ${provider.source.storageValue}")
                            return@withTimeoutOrNull CrossProviderFallbackResult(
                                source = provider.source,
                                resourceId = match.id.value,
                                url = resolution.url,
                                requestHeaders = resolution.requestHeaders,
                                actualQuality = resolution.actualQuality ?: resolution.requestedQuality,
                                expiresAtEpochMs = resolution.expiresAtEpochMs,
                            )
                        }
                        eventLogger(
                            "playback ${provider.source.storageValue} rejected: " +
                                resolution.javaClass.simpleName +
                                (lastQuality?.let { " at ${it.name}" } ?: ""),
                        )
                    }
                }
                null
            }
            if (resolved == null) eventLogger("unresolved or timed out song=${request.songId}")
            resolved
        }
    }

    private fun playbackQualityCandidates(requested: AudioQualityTier): List<AudioQualityTier> =
        when (requested) {
            AudioQualityTier.Standard -> listOf(AudioQualityTier.Standard)
            AudioQualityTier.High -> listOf(AudioQualityTier.High, AudioQualityTier.Standard)
            AudioQualityTier.Lossless -> listOf(
                AudioQualityTier.Lossless,
                AudioQualityTier.High,
                AudioQualityTier.Standard,
            )
            AudioQualityTier.HiResolution,
            AudioQualityTier.Immersive,
            AudioQualityTier.Master,
            -> listOf(requested, AudioQualityTier.High, AudioQualityTier.Standard)
        }.distinct()

    private fun errorLabel(error: Throwable): String {
        val detail = error.message
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(160)
            ?.takeIf(String::isNotBlank)
        return error.javaClass.simpleName + (detail?.let { ": $it" } ?: "")
    }

    private fun isEligibleFallbackProvider(provider: MusicProvider): Boolean =
        provider.sourceId.builtIn in EligibleSources && provider is SearchCapability && provider is PlaybackCapability

    private fun providerMetadata(request: CrossProviderFallbackRequest): ProviderTrackMetadata = when (request.source) {
        MusicSource.Netease -> ProviderTrackMetadata.Netease(request.songId)
        MusicSource.QQMusic -> ProviderTrackMetadata.QQMusic(request.resourceId)
        MusicSource.Kugou, MusicSource.KugouLite -> ProviderTrackMetadata.Kugou(request.resourceId)
        MusicSource.Kuwo -> ProviderTrackMetadata.Kuwo(request.songId)
        else -> ProviderTrackMetadata.Empty
    }

    private fun rankBilibiliMatches(
        source: MusicTrack,
        candidates: List<MusicTrack>,
    ): List<MusicTrack> {
        val titleKeys = bilibiliTitleKeys(source.title)
        if (titleKeys.isEmpty()) return emptyList()
        val sourceArtists = source.artists
            .map { TrackAggregation.normalizeArtist(it.name) }
            .filter(String::isNotBlank)
        return candidates.filter { candidate ->
            val candidateTitle = TrackAggregation.normalize(candidate.title)
            val titleMatches = titleKeys.any { key ->
                key.length >= 3 && candidateTitle.contains(key)
            }
            if (!titleMatches) return@filter false
            val candidateArtists = candidate.artists
                .map { TrackAggregation.normalizeArtist(it.name) }
                .filter(String::isNotBlank)
            val artistMatches = sourceArtists.any { sourceArtist ->
                candidateArtists.any { candidateArtist ->
                    candidateArtist == sourceArtist ||
                        candidateArtist.contains(sourceArtist) ||
                        sourceArtist.contains(candidateArtist)
                }
            }
            val durationMatches = source.durationMs != null && candidate.durationMs != null &&
                abs(source.durationMs - candidate.durationMs) <= BilibiliDurationToleranceMs
            artistMatches || durationMatches
        }
    }

    private fun bilibiliTitleKeys(value: String): Set<String> = buildList {
        add(value)
        for (delimiter in listOf('(', '（', '[', '【', '『')) {
            add(value.substringBefore(delimiter))
        }
    }.map(TrackAggregation::normalize)
        .filter { it.length >= 3 }
        .toSet()

    private fun artistSet(track: MusicTrack): Set<String> = track.artists
        .map { TrackAggregation.normalizeArtist(it.name) }
        .filter(String::isNotBlank)
        .toSet()

    companion object {
        private const val BilibiliDurationToleranceMs = 10_000L

        /**
         * Sources that can return a complete, directly playable audio URL.
         *
         * Public providers decide internally whether to use the signed-in session
         * or the anonymous endpoint; inclusion here never requires the user to be
         * logged in. Jellyfin is the exception: it only participates when the user
         * has configured their own server, and is ordered last.
         */
        val EligibleSources = setOf(
            MusicSource.Netease,
            MusicSource.QQMusic,
            MusicSource.KugouLite,
            MusicSource.Qishui,
            MusicSource.Kuwo,
            MusicSource.Bilibili,
            MusicSource.YouTubeMusic,
            MusicSource.Jellyfin,
        )

        internal fun splitArtists(value: String): List<String> = value
            .split(Regex("\\s*(?:/|、|，|,)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
    }
}
