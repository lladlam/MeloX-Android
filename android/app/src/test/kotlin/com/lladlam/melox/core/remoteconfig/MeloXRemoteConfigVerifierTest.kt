package com.lladlam.melox.core.remoteconfig

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXRemoteConfigVerifierTest {
    @Test
    fun verifiesPublishedVersionOneEnvelopeWithPinnedProductionKey() {
        val verified = MeloXRemoteConfigVerifier().verify(fixture())

        assertEquals(MeloXRemoteConfigVerifier.KeyId, verified.keyId)
        assertEquals(2, verified.config.configVersion)
        assertTrue(verified.config.appliesTo(11))
        assertFalse(verified.config.appliesTo(21))
        assertEquals(listOf("qq_music", "kugou", "bilibili"), verified.config.fallback.order)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMutatedPublishedPayload() {
        val envelope = JSONObject(fixture())
        val payload = envelope.getString("payload")
        envelope.put("payload", payload.dropLast(1) + if (payload.last() == 'A') "B" else "A")

        MeloXRemoteConfigVerifier().verify(envelope.toString())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSigningKey() {
        val envelope = JSONObject(fixture()).put("keyId", "unknown-key")

        MeloXRemoteConfigVerifier().verify(envelope.toString())
    }

    private fun fixture(): String = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("remote-config-v1-envelope.json"),
    ).bufferedReader().use { it.readText() }
}
