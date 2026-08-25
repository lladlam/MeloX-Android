package com.lladlam.melox.core.provider.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyOAuthTest {
    @Test
    fun createsRfc7636Challenge() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", SpotifyOAuthLogic.codeChallenge(verifier))
    }

    @Test
    fun stateMustMatchExactly() {
        assertTrue(SpotifyOAuthLogic.stateMatches("safe-state", "safe-state"))
        assertFalse(SpotifyOAuthLogic.stateMatches("safe-state", "safe-State"))
        assertFalse(SpotifyOAuthLogic.stateMatches("safe-state", null))
    }

    @Test
    fun oauthTransactionRejectsExpiredAndFutureTimestamps() {
        assertTrue(SpotifyOAuthLogic.transactionIsFresh(1_000L, 1_500L, 500L))
        assertFalse(SpotifyOAuthLogic.transactionIsFresh(1_000L, 1_501L, 500L))
        assertFalse(SpotifyOAuthLogic.transactionIsFresh(1_001L, 1_000L, 500L))
    }

    @Test
    fun parsesAccessAndOptionalRefreshToken() {
        val token = SpotifyOAuthLogic.parseToken("""{"access_token":"access","expires_in":3600,"token_type":"Bearer"}""")
        assertEquals("access", token.accessToken)
        assertEquals(3600L, token.expiresInSeconds)
        assertNull(token.refreshToken)
    }


    @Test
    fun sessionStringNeverContainsTokens() {
        val value = SpotifySession("secret-access", "secret-refresh", 123L, "account").toString()
        assertFalse(value.contains("secret-access"))
        assertFalse(value.contains("secret-refresh"))
        assertTrue(value.contains("account"))
    }
}
