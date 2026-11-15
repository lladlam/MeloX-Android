package com.lladlam.melox.playback

import com.lladlam.melox.core.audio.MeloXPlaybackUnavailableException
import com.lladlam.melox.core.audio.NeteasePlaybackUnavailableException
import com.lladlam.melox.core.music.model.PlaybackResolution

/**
 * Some built-in providers throw a terminal no-source exception instead of
 * returning Unavailable. Normalize only that documented outcome, otherwise the
 * existing fallback stage is never reached (notably for provider NetEase URIs).
 * Authentication, network and cancellation exceptions still propagate unchanged.
 */
internal suspend fun builtInPlaybackResult(
    fallbackEnabled: Boolean = true,
    resolve: suspend () -> PlaybackResolution,
): PlaybackResolution = try {
    resolve()
} catch (error: NeteasePlaybackUnavailableException) {
    if (!fallbackEnabled) throw error
    // Same membership/rights classification as upstream's legacy NetEase resolver.
    PlaybackResolution.SubscriptionRequired
} catch (error: MeloXPlaybackUnavailableException) {
    if (!fallbackEnabled) throw error
    PlaybackResolution.Unavailable(error.message)
}
