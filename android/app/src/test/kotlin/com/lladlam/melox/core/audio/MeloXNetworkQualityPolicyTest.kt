package com.lladlam.melox.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class MeloXNetworkQualityPolicyTest {
    private val fallback = MusicQuality.Standard
    private val wifi = MusicQuality.Lossless
    private val mobile = MusicQuality.High

    @Test
    fun disabledPolicyUsesTheExplicitDefault() {
        assertEquals(
            fallback,
            MeloXNetworkQualityPolicy.resolveEffectiveQuality(false, true, wifi, mobile, fallback),
        )
    }

    @Test
    fun enabledPolicySelectsTheActiveTransportPreset() {
        assertEquals(
            wifi,
            MeloXNetworkQualityPolicy.resolveEffectiveQuality(true, false, wifi, mobile, fallback),
        )
        assertEquals(
            mobile,
            MeloXNetworkQualityPolicy.resolveEffectiveQuality(true, true, wifi, mobile, fallback),
        )
    }

    @Test
    fun unknownTransportKeepsTheExplicitDefault() {
        assertEquals(
            fallback,
            MeloXNetworkQualityPolicy.resolveEffectiveQuality(true, null, wifi, mobile, fallback),
        )
    }
}
