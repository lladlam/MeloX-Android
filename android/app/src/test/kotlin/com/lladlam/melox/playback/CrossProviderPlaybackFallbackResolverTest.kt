package com.lladlam.melox.playback

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicPage
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.PlaybackResolution
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.music.provider.MusicProvider
import com.lladlam.melox.core.music.provider.MusicProviderRegistry
import com.lladlam.melox.core.music.provider.MusicCapability
import com.lladlam.melox.core.music.provider.PlaybackCapability
import com.lladlam.melox.core.music.provider.SearchCapability
import com.lladlam.melox.core.remoteconfig.MeloXRemoteConfigDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.delay

class CrossProviderPlaybackFallbackResolverTest {
    @Test
    fun resolvesOnlyStrictFullPlaybackMatch() {
        val wrong = track(MusicSource.QQMusic, artist = "Other Artist")
        val exact = track(MusicSource.Bilibili)
        val resolver = resolver(
            FakeProvider(MusicSource.QQMusic, listOf(wrong), playable = true),
            FakeProvider(MusicSource.Bilibili, listOf(exact), playable = true),
        )

        val result = resolver.resolve(request())

        assertEquals(MusicSource.Bilibili, result?.source)
        assertEquals(exact.id.value, result?.resourceId)
        assertEquals("https://audio.example/${exact.id.value}", result?.url)
    }

    @Test
    fun doesNotUsePreviewOrWrongArtist() {
        val resolver = resolver(
            FakeProvider(MusicSource.QQMusic, listOf(track(MusicSource.QQMusic)), playable = false),
            FakeProvider(
                MusicSource.Bilibili,
                listOf(
                    track(
                        MusicSource.Bilibili,
                        artist = "Other Artist",
                        durationMs = 100_000L,
                    ),
                ),
                playable = true,
            ),
        )

        assertNull(resolver.resolve(request()))
    }

    @Test
    fun unknownDurationUsesExactTitleAndCompleteArtistMatch() {
        val partialArtists = track(
            source = MusicSource.QQMusic,
            artist = "Primary Artist",
        )
        val exact = track(
            source = MusicSource.Bilibili,
            artists = listOf("Primary Artist", "Guest"),
        )
        val resolver = resolver(
            FakeProvider(MusicSource.QQMusic, listOf(partialArtists), playable = true),
            FakeProvider(MusicSource.Bilibili, listOf(exact), playable = true),
        )

        val result = resolver.resolve(
            request().copy(
                artist = "Primary Artist / Guest",
                durationMs = null,
            ),
        )

        assertEquals(MusicSource.Bilibili, result?.source)
        assertEquals(exact.id.value, result?.resourceId)
    }

    @Test
    fun disabledSettingDoesNotIssueSearch() {
        val provider = FakeProvider(MusicSource.QQMusic, listOf(track(MusicSource.QQMusic)), playable = true)
        val resolver = CrossProviderPlaybackFallbackResolver(
            enabledProvider = { false },
            registryProvider = { MusicProviderRegistry(listOf(provider)) },
        )

        assertNull(resolver.resolve(request()))
        assertEquals(0, provider.searchCount)
    }

    @Test
    fun remotePolicyExcludesDisabledProviderAndControlsOrder() {
        val qq = track(MusicSource.QQMusic)
        val bilibili = track(MusicSource.Bilibili)
        val resolver = CrossProviderPlaybackFallbackResolver(
            enabledProvider = { true },
            registryProvider = {
                MusicProviderRegistry(
                    listOf(
                        FakeProvider(MusicSource.QQMusic, listOf(qq), playable = true),
                        FakeProvider(MusicSource.Bilibili, listOf(bilibili), playable = true),
                    ),
                )
            },
            fallbackConfigProvider = {
                MeloXRemoteConfigDefaults.Config.fallback.copy(
                    order = listOf("bilibili", "qq_music"),
                    disabledProviders = setOf("qq_music"),
                )
            },
        )

        val result = resolver.resolve(request())

        assertEquals(MusicSource.Bilibili, result?.source)
    }

    @Test
    fun artistListPreservesPrimaryArtistOrder() {
        assertEquals(
            listOf("Primary Artist", "Guest"),
            CrossProviderPlaybackFallbackResolver.splitArtists("Primary Artist / Guest"),
        )
    }

    @Test
    fun retiredStandardKugouIsIgnoredEvenWhenRemotePolicyListsIt() {
        val standard = FakeProvider(MusicSource.Kugou, listOf(track(MusicSource.Kugou)), playable = true)
        assertNull(resolver(standard).resolve(request()))
        assertEquals(0, standard.searchCount)
    }

    @Test
    fun youtubeMusicIsEligibleWithoutAnyCredential() {
        val youtube = track(MusicSource.YouTubeMusic)
        val provider = FakeProvider(MusicSource.YouTubeMusic, listOf(youtube), playable = true)

        val result = resolver(provider).resolve(request())

        assertEquals(MusicSource.YouTubeMusic, result?.source)
        assertEquals(1, provider.searchCount)
    }

    @Test
    fun configuredJellyfinServerCanServeAsTheLastResort() {
        val jellyfin = track(MusicSource.Jellyfin)
        val provider = FakeProvider(MusicSource.Jellyfin, listOf(jellyfin), playable = true)

        val result = resolver(provider).resolve(request())

        assertEquals(MusicSource.Jellyfin, result?.source)
        assertEquals(1, provider.searchCount)
    }

    @Test
    fun defaultOrderAndRemoteAllowListCoverEveryEligibleSource() {
        val eligible = CrossProviderPlaybackFallbackResolver.EligibleSources
            .map(MusicSource::storageValue)
            .toSet()

        assertEquals(eligible, MeloXRemoteConfigDefaults.AllowedProviders)
        assertEquals(eligible, MeloXRemoteConfigDefaults.FallbackOrder.toSet())
        assertEquals(eligible.size, MeloXRemoteConfigDefaults.FallbackOrder.size)
    }

    @Test
    fun fallsBackToALowerQualityWhenTheRequestedTierIsUnavailable() {
        val standardOnly = FakeProvider(
            source = MusicSource.QQMusic,
            tracks = listOf(track(MusicSource.QQMusic)),
            playable = true,
            maxPlayableQuality = AudioQualityTier.Standard,
        )

        val result = resolver(standardOnly).resolve(request())

        assertEquals(MusicSource.QQMusic, result?.source)
        assertEquals(AudioQualityTier.Standard, result?.actualQuality)
    }

    @Test
    fun bilibiliMatchesContainedTitleAndUploader() {
        val official = track(
            MusicSource.Bilibili,
            title = "尽情享受吧！因为『今天不是明天』✨【兰音原创曲】",
            artist = "兰音Reine",
            durationMs = 196_000L,
        )

        val result = resolver(FakeProvider(MusicSource.Bilibili, listOf(official), playable = true))
            .resolve(
                request().copy(
                    title = "今天不是明天 (feat. 兰音Reine)",
                    artist = "电音联盟 / 兰音Reine",
                    durationMs = 261_000L,
                ),
            )

        assertEquals(MusicSource.Bilibili, result?.source)
        assertEquals(official.id.value, result?.resourceId)
    }

    @Test
    fun bilibiliMatchesContainedTitleAndDurationWhenUploaderDiffers() {
        val cover = track(
            MusicSource.Bilibili,
            title = "【翻唱】今天不是明天",
            artist = "某UP主",
            durationMs = 261_000L,
        )

        val result = resolver(FakeProvider(MusicSource.Bilibili, listOf(cover), playable = true))
            .resolve(
                request().copy(
                    title = "今天不是明天 (feat. 兰音Reine)",
                    artist = "电音联盟 / 兰音Reine",
                    durationMs = 261_000L,
                ),
            )

        assertEquals(MusicSource.Bilibili, result?.source)
        assertEquals(cover.id.value, result?.resourceId)
    }

    @Test
    fun bilibiliRejectsTitleContainingWrongUploaderAndDuration() {
        val unrelated = track(
            MusicSource.Bilibili,
            title = "今天不是明天的钢琴改编",
            artist = "其他UP主",
            durationMs = 120_000L,
        )

        assertNull(
            resolver(FakeProvider(MusicSource.Bilibili, listOf(unrelated), playable = true))
                .resolve(
                    request().copy(
                        title = "今天不是明天 (feat. 兰音Reine)",
                        artist = "电音联盟 / 兰音Reine",
                        durationMs = 261_000L,
                    ),
                ),
        )
    }

    @Test
    fun slowProviderCannotConsumeTheWholeFallbackBudget() {
        val slow = FakeProvider(
            source = MusicSource.Bilibili,
            tracks = listOf(track(MusicSource.Bilibili)),
            playable = true,
            searchDelayMs = 10_000L,
        )
        val fast = FakeProvider(
            source = MusicSource.QQMusic,
            tracks = listOf(track(MusicSource.QQMusic)),
            playable = true,
        )
        val resolver = CrossProviderPlaybackFallbackResolver(
            enabledProvider = { true },
            registryProvider = { MusicProviderRegistry(listOf(slow, fast)) },
            fallbackConfigProvider = {
                MeloXRemoteConfigDefaults.Config.fallback.copy(
                    order = listOf("bilibili", "qq_music"),
                    timeoutMs = 3_000,
                )
            },
            perProviderTimeoutMs = 100L,
        )

        val startedAt = System.nanoTime()
        val result = resolver.resolve(request())

        assertEquals(MusicSource.QQMusic, result?.source)
        assertTrue((System.nanoTime() - startedAt) / 1_000_000L < 2_000L)
    }

    private fun resolver(vararg providers: MusicProvider) = CrossProviderPlaybackFallbackResolver(
        enabledProvider = { true },
        registryProvider = { MusicProviderRegistry(providers.asList()) },
    )

    private fun request() = CrossProviderFallbackRequest(
        songId = 100L,
        title = "A Song",
        artist = "The Artist",
        durationMs = 200_000L,
        quality = AudioQualityTier.HiResolution,
    )

    private fun track(
        source: MusicSource,
        title: String = "A Song",
        artist: String = "The Artist",
        artists: List<String> = listOf(artist),
        durationMs: Long = 200_000L,
    ) = MusicTrack(
        id = MusicResourceId(source, "${source.storageValue}-$title-$artist"),
        title = title,
        artists = artists.map { MusicArtistRef(name = it) },
        durationMs = durationMs,
        availability = TrackAvailability.Playable,
    )

    private class FakeProvider(
        override val source: MusicSource,
        private val tracks: List<MusicTrack>,
        private val playable: Boolean,
        private val searchDelayMs: Long = 0L,
        private val maxPlayableQuality: AudioQualityTier = AudioQualityTier.Master,
    ) : MusicProvider, SearchCapability, PlaybackCapability {
        override val displayName = source.displayName
        override val capabilities = setOf(MusicCapability.Search, MusicCapability.Playback)
        var searchCount = 0
            private set

        override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> {
            searchCount += 1
            if (searchDelayMs > 0L) delay(searchDelayMs)
            assertTrue(query.isNotBlank())
            return MusicPage(tracks, page, pageSize, tracks.size.toLong())
        }

        override suspend fun resolvePlayback(
            track: MusicTrack,
            quality: AudioQualityTier,
        ): PlaybackResolution = when {
            !playable -> PlaybackResolution.Preview("https://preview.example/${track.id.value}")
            quality.ordinal > maxPlayableQuality.ordinal ->
                PlaybackResolution.Unavailable("quality unavailable")
            else -> PlaybackResolution.Playable(
                url = "https://audio.example/${track.id.value}",
                requestHeaders = mapOf("Referer" to "https://example.com/"),
                requestedQuality = quality,
                actualQuality = quality,
            )
        }
    }
}
