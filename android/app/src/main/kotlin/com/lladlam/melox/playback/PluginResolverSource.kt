package com.lladlam.melox.playback

import android.util.Log
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.plugin.PluginMusicProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Final-stage adapter for API-v1 plugins that declared the `resolver` capability.
 *
 * A resolver plugin receives the original track metadata (including its source)
 * and may return a playable URL. It never runs for plugin-owned tracks: those
 * keep using the plugin's own playback path so a resolver cannot silently
 * replace a plugin track with arbitrary audio.
 */
internal class PluginResolverSource(
    private val plugins: () -> List<PluginMusicProvider>,
    private val timeoutMs: Long = 9_000L,
) : ThirdPartySource {
    override fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack? {
        for (plugin in plugins()) {
            if (!plugin.supports(MusicCapability.Resolver)) continue
            val resolution = runCatching {
                runBlocking(Dispatchers.IO) {
                    withTimeout(timeoutMs) { plugin.resolveFallback(track, quality) }
                }
            }.onFailure { error ->
                // Never log the track URL, plugin arguments or response body.
                Log.w(TAG, "Resolver plugin ${plugin.sourceId.value} failed: ${error.javaClass.simpleName}")
            }.getOrNull() ?: continue

            if (resolution is PlaybackResolution.Playable) {
                if (!resolution.url.startsWith("http://") && !resolution.url.startsWith("https://")) continue
                Log.i(
                    TAG,
                    "Resolved song=${track.id.sourceId.value}:${track.id.value} via ${plugin.sourceId.value} " +
                        "requested=${quality.name} actual=${resolution.actualQuality?.name ?: "unknown"}",
                )
                return ThirdPartyTrack(
                    url = resolution.url,
                    requestHeaders = resolution.requestHeaders,
                    sourceLabel = "plugin:${plugin.sourceId.value.removePrefix("plugin:")}",
                    sourceQuality = quality.name,
                    actualQuality = resolution.actualQuality,
                    expiresAtEpochMs = resolution.expiresAtEpochMs,
                )
            }
            // Login/subscription/region/preview results cannot satisfy the
            // full-audio fallback contract; try the next resolver plugin.
        }
        return null
    }

    override fun cacheIdentity(): String =
        plugins()
            .filter { it.supports(MusicCapability.Resolver) }
            .joinToString("|") { "${it.sourceId.value}@${it.manifest.version}" }
            .ifEmpty { "none" }

    private companion object {
        const val TAG = "MeloXPluginResolver"
    }
}
