package com.lladlam.melox.core.remoteconfig

import com.lladlam.melox.core.music.model.MusicSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXRemoteConfigPolicyTest {
    private val remote = MeloXRemoteConfigDefaults.Config.copy(
        configVersion = 4,
        disabledCapabilities = setOf("qq_playback", "cross_provider_fallback"),
    )
    private val verified = MeloXRemoteConfigStatus(
        source = MeloXRemoteConfigSource.VerifiedRemote,
        config = remote,
    )

    @Test
    fun refusalAlwaysUsesBuiltInDefaults() {
        assertSame(
            MeloXRemoteConfigDefaults.Config,
            MeloXRemoteConfigPolicy.effectiveConfig(consentEnabled = false, status = verified),
        )
    }

    @Test
    fun consentAppliesOnlyVerifiedApplicableRemoteConfig() {
        assertSame(remote, MeloXRemoteConfigPolicy.effectiveConfig(consentEnabled = true, status = verified))
        assertSame(
            MeloXRemoteConfigDefaults.Config,
            MeloXRemoteConfigPolicy.effectiveConfig(
                consentEnabled = true,
                status = verified.copy(source = MeloXRemoteConfigSource.VersionInapplicable),
            ),
        )
    }

    @Test
    fun playbackKillSwitchAffectsOnlyDeclaredProvider() {
        assertFalse(MeloXRemoteConfigPolicy.providerPlaybackEnabled(remote, MusicSource.QQMusic))
        assertTrue(MeloXRemoteConfigPolicy.providerPlaybackEnabled(remote, MusicSource.Kugou))
        assertTrue(MeloXRemoteConfigPolicy.providerPlaybackEnabled(remote, MusicSource.Netease))
    }
}
