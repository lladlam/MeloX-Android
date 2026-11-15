package com.lladlam.melox.playback

import com.lladlam.melox.core.audio.MeloXPlaybackUnavailableException
import com.lladlam.melox.core.audio.NeteasePlaybackUnavailableException
import com.lladlam.melox.core.music.model.PlaybackResolution
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class BuiltInPlaybackResultTest {
    @Test fun terminalNoSourceEntersExistingFallbackStage() = runBlocking {
        assertEquals(PlaybackResolution.SubscriptionRequired,
            builtInPlaybackResult { throw NeteasePlaybackUnavailableException("no full URL") })
        val result = builtInPlaybackResult { throw MeloXPlaybackUnavailableException("unavailable") }
        assertTrue(result is PlaybackResolution.Unavailable)
    }

    @Test fun officialSuccessAndLoginResultsAreUnchanged() = runBlocking {
        val result = PlaybackResolution.Playable(
            url = "https://official.test/a.mp3",
            requestedQuality = com.lladlam.melox.core.music.model.AudioQualityTier.High,
        )
        assertSame(result, builtInPlaybackResult { result })
        assertEquals(PlaybackResolution.LoginRequired, builtInPlaybackResult { PlaybackResolution.LoginRequired })
    }

    @Test fun networkAndCancellationAreNotMisclassifiedAsNoSource() {
        for (error in listOf(IOException("offline"), CancellationException("cancelled"), IllegalArgumentException("invalid track"))) {
            val result = runCatching { runBlocking { builtInPlaybackResult { throw error } } }
            assertSame(error, result.exceptionOrNull())
        }
    }

    @Test fun providerTimeoutIsNotMisclassifiedAsNoSource() {
        val result = runCatching {
            runBlocking {
                builtInPlaybackResult { withTimeout(50L) { awaitCancellation() } }
            }
        }
        assertTrue(result.exceptionOrNull() is TimeoutCancellationException)
    }

    @Test fun disablingThirdPartyConsentPreservesUpstreamExceptionBehaviour() {
        val error = NeteasePlaybackUnavailableException("official unavailable")
        val result = runCatching { runBlocking { builtInPlaybackResult(false) { throw error } } }
        assertSame(error, result.exceptionOrNull())
    }

    @Test fun terminalOfficialFailureReachesConfiguredFallbackWhenEnabled() = runBlocking {
        var calls = 0
        val track = com.lladlam.melox.core.music.model.MusicTrack(
            com.lladlam.melox.core.music.model.MusicResourceId(
                com.lladlam.melox.core.music.model.MusicSource.Netease, "123",
            ), "", emptyList(),
        )
        val chain = PlaybackFallbackChain(null, ThirdPartyPlaybackResolver({ true }, { true }, listOf(
            ThirdPartySource { _, _ ->
                calls++
                ThirdPartyTrack("https://configured.test/audio.mp3", sourceLabel = "fixture")
            },
        )))
        val result = builtInPlaybackResult(true) { throw NeteasePlaybackUnavailableException("official unavailable") }
        assertEquals(PlaybackResolution.SubscriptionRequired, result)
        val resolved = chain.resolve(track, com.lladlam.melox.core.music.model.AudioQualityTier.High,
            membershipFailure = result == PlaybackResolution.SubscriptionRequired)
        assertEquals("https://configured.test/audio.mp3", resolved?.url)
        assertEquals(1, calls)
    }
}
