package com.lladlam.melox.playback

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack

internal data class PlaybackFallbackResolution(
    val url: String,
    val requestHeaders: Map<String, String>,
    val expiresAtEpochMs: Long?,
    val cacheIdentity: String,
    val crossProvider: CrossProviderFallbackResult?,
    val thirdParty: ThirdPartyTrack?,
)

/**
 * Pure ordering for the two fallback stages. Keeping it separate from Media3
 * makes the documented order testable without Android's Uri implementation:
 *
 * 1. cross-provider strict full-audio match,
 * 2. configured third-party resolvers.
 */
internal class PlaybackFallbackChain(
    private val crossProvider: CrossProviderPlaybackFallbackResolver?,
    private val thirdParty: ThirdPartyPlaybackResolver?,
) {
    fun cacheIdentity(): String =
        "${crossProvider?.cacheIdentity().orEmpty()}:${thirdParty?.cacheIdentity().orEmpty()}"

    fun resolve(
        track: MusicTrack,
        quality: AudioQualityTier,
        membershipFailure: Boolean,
    ): PlaybackFallbackResolution? {
        // Plugin transport and permissions never fall through into built-in/LX resolvers.
        val builtInSource = track.id.sourceId.builtIn ?: return null
        val cross = runCatching {
            crossProvider?.resolve(
                CrossProviderFallbackRequest(
                    songId = track.id.value.toLongOrNull() ?: 0L,
                    title = track.title,
                    artist = track.artistText,
                    durationMs = track.durationMs,
                    quality = quality,
                    source = builtInSource,
                    resourceId = track.id.value,
                ),
            )
        }.getOrNull()
        if (cross != null) {
            return PlaybackFallbackResolution(
                url = cross.url,
                requestHeaders = cross.requestHeaders,
                expiresAtEpochMs = cross.expiresAtEpochMs,
                cacheIdentity = "${cross.source.storageValue}:${cross.resourceId}",
                crossProvider = cross,
                thirdParty = null,
            )
        }
        val third = runCatching {
            thirdParty?.resolve(track, quality, membershipFailure)
        }.getOrNull() ?: return null
        return PlaybackFallbackResolution(
            url = third.url,
            requestHeaders = third.requestHeaders,
            expiresAtEpochMs = third.configuredResolverExpiry(),
            cacheIdentity = "third:${third.sourceLabel}",
            crossProvider = null,
            thirdParty = third,
        )
    }
}
