package com.lladlam.melox.core.remoteconfig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXRemoteConfigModelsTest {
    @Test
    fun unknownOptionalValuesFallBackWithoutExpandingRemoteAuthority() {
        val config = MeloXRemoteConfig.parse(
            """
            {
              "schemaVersion": 1,
              "configVersion": 8,
              "issuedAtEpochSeconds": 1,
              "minVersionCode": 10,
              "maxVersionCode": 20,
              "disabledCapabilities": ["qq_playback", "unknown_capability"],
              "strategies": {
                "netease_playback": "unknown",
                "qq_playback": "v2",
                "kugou_playback": "v1"
              },
              "crossProviderFallback": {
                "enabled": true,
                "order": ["unknown", "qq_music"],
                "disabledProviders": ["bilibili", "unknown"],
                "timeoutMs": 99999
              },
              "notice": null
            }
            """.trimIndent().toByteArray(),
        )

        assertEquals(setOf("qq_playback"), config.disabledCapabilities)
        assertEquals("v1", config.strategies["netease_playback"])
        assertEquals("v1", config.strategies["qq_playback"])
        assertEquals(MeloXRemoteConfigDefaults.FallbackOrder, config.fallback.order)
        assertEquals(setOf("bilibili"), config.fallback.disabledProviders)
        assertEquals(10_000, config.fallback.timeoutMs)
        assertTrue(config.appliesTo(11))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSchema() {
        MeloXRemoteConfig.parse(
            """{"schemaVersion":2,"configVersion":1}""".toByteArray(),
        )
    }
}
