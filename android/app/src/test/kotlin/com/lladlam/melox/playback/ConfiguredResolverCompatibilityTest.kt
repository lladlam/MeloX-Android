package com.lladlam.melox.playback

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import org.junit.Assert.*
import org.junit.Test

class ConfiguredResolverCompatibilityTest {
    private val track = MusicTrack(MusicResourceId(MusicSource.Netease, "123"), "Test", emptyList())

    @Test fun configuredApiExpiryDoesNotDependOnReportedQualityFields() {
        for (label in listOf("Meting", "CHKSZ", "UNM")) {
            val result = ThirdPartyTrack("https://audio.test/song", sourceLabel = label)
            assertEquals(130_000L, result.configuredResolverExpiry(10_000L))
            assertNull(result.sourceQuality)
            assertNull(result.actualQuality)
        }
        assertNull(ThirdPartyTrack("https://audio.test/song", sourceLabel = "lx:script").configuredResolverExpiry(10_000L))
    }

    @Test fun installedScriptGuardPreservesUpstreamQualityAndHeaders() {
        val result = ThirdPartyTrack(
            url = "https://audio.test/song.flac", requestHeaders = mapOf("Referer" to "https://audio.test"),
            sourceLabel = "lx:script", sourceQuality = "flac", actualQuality = AudioQualityTier.Lossless,
        )
        val source = object : ThirdPartySource {
            override fun cacheIdentity() = "lx:script"
            override fun resolve(track: MusicTrack, quality: AudioQualityTier) = result
        }
        val guarded = withInstalledLxGuard(source)
        assertSame(result, guarded.resolve(track, AudioQualityTier.High))
        assertEquals("lx:script", guarded.cacheIdentity())
        assertEquals("flac", result.sourceQuality)
        assertEquals(AudioQualityTier.Lossless, result.actualQuality)
    }

    @Test fun installingOrRemovingScriptsIsReadForEachResolution() {
        var identity = "lx:"
        var requests = 0
        val guarded = withInstalledLxGuard(object : ThirdPartySource {
            override fun cacheIdentity() = identity
            override fun resolve(track: MusicTrack, quality: AudioQualityTier): ThirdPartyTrack {
                requests++
                return ThirdPartyTrack("https://audio.test/song.mp3", sourceLabel = identity)
            }
        })
        assertNull(guarded.resolve(track, AudioQualityTier.High))
        assertEquals(0, requests)
        identity = "lx:new-script"
        assertNotNull(guarded.resolve(track, AudioQualityTier.High))
        assertEquals(1, requests)
        identity = "lx:"
        assertNull(guarded.resolve(track, AudioQualityTier.High))
        assertEquals(1, requests)
    }
}
