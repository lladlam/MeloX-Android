package com.lladlam.melox.playback

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.MusicProviderRegistry
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks the documented order: current source → cross-provider → third-party. */
class PlaybackFallbackOrderTest {
    @Test
    fun crossProviderFollowsNeteaseQqKugouQishuiAndSkipsCurrentSource() {
        val calls = mutableListOf<MusicSource>()
        val providers = listOf(
            FakeProvider(MusicSource.Netease, calls),
            FakeProvider(MusicSource.QQMusic, calls),
            FakeProvider(MusicSource.KugouLite, calls),
            FakeProvider(MusicSource.Qishui, calls),
        )
        val resolver = CrossProviderPlaybackFallbackResolver(
            enabledProvider = { true },
            registryProvider = { MusicProviderRegistry(providers) },
        )

        val result = resolver.resolve(
            CrossProviderFallbackRequest(
                songId = 0L,
                title = "A Song",
                artist = "The Artist",
                durationMs = 200_000L,
                quality = AudioQualityTier.High,
                source = MusicSource.Qishui,
                resourceId = "qishui-id",
            ),
        )

        assertEquals(MusicSource.Netease, result?.source)
        assertEquals(listOf(MusicSource.Netease), calls)
    }

    @Test
    fun currentSourceIsNotSearchedAgain() {
        val calls = mutableListOf<MusicSource>()
        val providers = listOf(
            FakeProvider(MusicSource.QQMusic, calls, playable = false),
            FakeProvider(MusicSource.KugouLite, calls),
        )
        val resolver = CrossProviderPlaybackFallbackResolver(
            enabledProvider = { true },
            registryProvider = { MusicProviderRegistry(providers) },
        )

        val result = resolver.resolve(
            CrossProviderFallbackRequest(
                songId = 0L,
                title = "A Song",
                artist = "The Artist",
                durationMs = 200_000L,
                quality = AudioQualityTier.High,
                source = MusicSource.QQMusic,
                resourceId = "qq-id",
            ),
        )

        assertEquals(MusicSource.KugouLite, result?.source)
        assertEquals(listOf(MusicSource.KugouLite), calls)
    }

    @Test
    fun thirdPartyRunsOnlyAfterCrossProviderFailed() {
        val calls = mutableListOf<String>()
        val thirdParty = thirdParty(calls)
        val chain = PlaybackFallbackChain(
            crossProvider = CrossProviderPlaybackFallbackResolver(
                enabledProvider = { true },
                registryProvider = { MusicProviderRegistry(emptyList()) },
            ),
            thirdParty = thirdParty,
        )

        val result = chain.resolve(track(MusicSource.QQMusic), AudioQualityTier.High, membershipFailure = false)

        assertEquals("https://third.example/song", result?.url)
        assertEquals(listOf("third"), calls)
    }

    @Test
    fun crossProviderWinsBeforeThirdParty() {
        val calls = mutableListOf<String>()
        val qq = FakeProvider(MusicSource.QQMusic, mutableListOf(), playable = false)
        val netease = FakeProvider(MusicSource.Netease, mutableListOf())
        val registry = MusicProviderRegistry(listOf(qq, netease))
        val chain = PlaybackFallbackChain(
            crossProvider = CrossProviderPlaybackFallbackResolver(
                enabledProvider = { true },
                registryProvider = { registry },
            ),
            thirdParty = thirdParty(calls),
        )

        val result = chain.resolve(track(MusicSource.QQMusic), AudioQualityTier.High, membershipFailure = true)

        assertEquals("https://audio.example/netease", result?.url)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun everyStageOffLeavesTrackUnavailable() {
        val chain = PlaybackFallbackChain(
            crossProvider = CrossProviderPlaybackFallbackResolver(
                enabledProvider = { false },
                registryProvider = { MusicProviderRegistry(emptyList()) },
            ),
            thirdParty = ThirdPartyPlaybackResolver(
                enabledProvider = { false },
                membershipOnlyProvider = { false },
                sources = listOf(
                    ThirdPartySource { _, _ ->
                        ThirdPartyTrack("https://third.example/song", sourceLabel = "test")
                    },
                ),
            ),
        )

        assertNull(chain.resolve(track(MusicSource.Qishui), AudioQualityTier.High, membershipFailure = false))
    }

    @Test
    fun membershipOnlyOptionSkipsThirdPartyForNonMembershipFailures() {
        var called = false
        val resolver = ThirdPartyPlaybackResolver(
            enabledProvider = { true },
            membershipOnlyProvider = { true },
            sources = listOf(
                ThirdPartySource { _, _ ->
                    called = true
                    ThirdPartyTrack("https://third.example/song", sourceLabel = "test")
                },
            ),
        )

        val result = resolver.resolve(track(MusicSource.Qishui), AudioQualityTier.High, membershipFailure = false)

        assertNull(result)
        assertTrue(!called)
    }

    @Test
    fun membershipFailureStillUsesConfiguredThirdPartySource() {
        val resolver = ThirdPartyPlaybackResolver(
            enabledProvider = { true },
            membershipOnlyProvider = { true },
            sources = listOf(
                ThirdPartySource { _, _ -> ThirdPartyTrack("https://third.example/song", sourceLabel = "test") },
            ),
        )

        val result = resolver.resolve(track(MusicSource.Qishui), AudioQualityTier.High, membershipFailure = true)

        assertEquals("https://third.example/song", result?.url)
    }

    @Test
    fun idBasedResolversDoNotRequireTitleMetadata() {
        val calls = mutableListOf<String>()
        val result = thirdParty(calls).resolve(
            track(MusicSource.Netease).copy(title = "", artists = emptyList()),
            AudioQualityTier.High, membershipFailure = true,
        )
        assertEquals("https://third.example/song", result?.url)
        assertEquals(listOf("third"), calls)
    }

    @Test
    fun pluginTracksNeverEnterBuiltInThirdPartySources() {
        val calls = mutableListOf<String>()
        val plugin = track(MusicSource.Netease).copy(id = MusicResourceId(
            com.lladlam.melox.core.music.model.SourceId.parse("plugin:org.example.test")!!, "1",
        ))
        assertNull(thirdParty(calls).resolve(plugin, AudioQualityTier.High, true))
        assertTrue(calls.isEmpty())
    }

    @Test
    fun apiUrlExpirySurvivesTheFallbackChain() {
        val chain = PlaybackFallbackChain(null, ThirdPartyPlaybackResolver({ true }, { false }, listOf(
            ThirdPartySource { _, _ -> ThirdPartyTrack("https://third.example/audio", sourceLabel = "Meting") },
        )))
        val before = System.currentTimeMillis()
        val expires = chain.resolve(track(MusicSource.Netease), AudioQualityTier.High, false)?.expiresAtEpochMs
        val after = System.currentTimeMillis()
        assertTrue(expires != null && expires in (before + 120_000L)..(after + 120_000L))
    }

    private fun thirdParty(calls: MutableList<String>) = ThirdPartyPlaybackResolver(
        enabledProvider = { true },
        membershipOnlyProvider = { false },
        sources = listOf(
            ThirdPartySource { _, _ ->
                calls += "third"
                ThirdPartyTrack("https://third.example/song", sourceLabel = "test")
            },
        ),
    )

    private fun track(source: MusicSource) = MusicTrack(
        id = MusicResourceId(source, "${source.storageValue}-id"),
        title = "A Song",
        artists = listOf(MusicArtistRef(name = "The Artist")),
        durationMs = 200_000L,
        availability = TrackAvailability.Playable,
    )

    private class FakeProvider(
        override val source: MusicSource,
        private val calls: MutableList<MusicSource>,
        private val playable: Boolean = true,
    ) : MusicProvider, SearchCapability, PlaybackCapability {
        override val displayName = source.displayName
        override val capabilities = setOf(MusicCapability.Search, MusicCapability.Playback)

        override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> {
            calls += source
            val track = MusicTrack(
                id = MusicResourceId(source, "${source.storageValue}-match"),
                title = "A Song",
                artists = listOf(MusicArtistRef(name = "The Artist")),
                durationMs = 200_000L,
                availability = TrackAvailability.Playable,
            )
            return MusicPage(listOf(track), page, pageSize, 1)
        }

        override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
            if (playable) {
                PlaybackResolution.Playable(
                    url = "https://audio.example/${source.storageValue}",
                    requestedQuality = quality,
                    actualQuality = AudioQualityTier.High,
                )
            } else {
                PlaybackResolution.Unavailable("no stream")
            }
    }
}
