package com.lladlam.melox.core.audio

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NeteaseQualityClientPolicyTest {
    @Test
    fun anonymousExplicitUnavailableSourceTriggersCrossProviderFallback() {
        val error = NeteaseQualityClient.terminalPlaybackFailure(
            loggedIn = false,
            serverReportedUnavailable = true,
            requestedQuality = MusicQuality.Standard,
            lastError = null,
        )

        assertTrue(error is NeteasePlaybackUnavailableException)
    }

    @Test
    fun anonymousTransportFailureStillUsesOuterUrlFallback() {
        val error = NeteaseQualityClient.terminalPlaybackFailure(
            loggedIn = false,
            serverReportedUnavailable = false,
            requestedQuality = MusicQuality.Standard,
            lastError = null,
        )

        assertNull(error)
    }
}
