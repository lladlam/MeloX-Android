package com.lladlam.melox.core.music.provider

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.TrackAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackAggregationTest {
    private fun track(
        source: MusicSource,
        title: String,
        duration: Long = 240_000L,
        quality: AudioQualityTier? = null,
        bitrate: Int? = null,
        availability: TrackAvailability = TrackAvailability.Playable,
    ) = MusicTrack(
        id = MusicResourceId(source, title + source.storageValue),
        title = title,
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        durationMs = duration,
        availability = availability,
        bestQuality = quality,
        bestBitrate = bitrate,
    )

    @Test
    fun favoriteIndexPreservesDurationTolerance() {
        val index = FavoriteTrackIndex(listOf(track(MusicSource.Netease, "晴天", duration = 240_000L)))

        assertTrue(index.contains(track(MusicSource.QQMusic, "晴天", duration = 249_000L)))
        assertTrue(!index.contains(track(MusicSource.QQMusic, "晴天", duration = 251_000L)))
    }

    @Test
    fun favoriteIndexMatchesUnknownDurationsButKeepsVersionsSeparate() {
        val original = track(MusicSource.Netease, "晴天")
        val index = FavoriteTrackIndex(listOf(original))

        assertTrue(index.contains(original.copy(durationMs = null)))
        assertTrue(index.contains(original.copy(durationMs = 0L)))
        assertTrue(!index.contains(original.copy(title = "晴天 (Live)")))
        assertTrue(!index.contains(original.copy(title = "夜曲")))
        assertTrue(FavoriteTrackIndex(listOf(original.copy(durationMs = null)))
            .contains(original.copy(durationMs = 600_000L)))
        assertTrue(!FavoriteTrackIndex(emptyList()).contains(original))
    }

    @Test fun sameStudioTracksAreAggregated() {
        val groups = TrackAggregation.aggregate(listOf(track(MusicSource.Netease, "晴天"), track(MusicSource.QQMusic, "晴天")))
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().candidates.size)
    }

    @Test fun liveVersionDoesNotMergeWithStudio() {
        val groups = TrackAggregation.aggregate(listOf(track(MusicSource.Netease, "晴天"), track(MusicSource.QQMusic, "晴天 (Live)")))
        assertEquals(2, groups.size)
    }

    @Test fun playableCandidateWins() {
        val groups = TrackAggregation.aggregate(listOf(track(MusicSource.Netease, "晴天"), track(MusicSource.QQMusic, "晴天")))
        assertTrue(groups.single().recommendation != null)
    }

    @Test fun higherAdvertisedQualityWinsForTheSameSong() {
        val netease = track(MusicSource.Netease, "晴天", quality = AudioQualityTier.High, bitrate = 320_000)
        val qq = track(MusicSource.QQMusic, "晴天", quality = AudioQualityTier.Lossless, bitrate = 1_000_000)
        val recommendation = TrackAggregation.aggregate(listOf(netease, qq)).single().recommendation
        assertEquals(MusicSource.QQMusic, recommendation?.track?.id?.source)
    }

    @Test fun losslessFromAnotherSourceBeatsSourcePreference() {
        val kuwo = track(MusicSource.Kuwo, "晴天", quality = AudioQualityTier.Lossless, bitrate = 1_000_000)
        val qq = track(MusicSource.QQMusic, "晴天", quality = AudioQualityTier.High, bitrate = 320_000)
        val recommendation = TrackAggregation.aggregate(listOf(qq, kuwo)).single().recommendation
        assertEquals(MusicSource.Kuwo, recommendation?.track?.id?.source)
    }

    @Test fun playableStandardBeatsNonPlayableLossless() {
        val playable = track(MusicSource.Netease, "晴天", quality = AudioQualityTier.Standard, bitrate = 128_000)
        val unavailable = track(
            MusicSource.QQMusic,
            "晴天",
            quality = AudioQualityTier.Lossless,
            bitrate = 1_000_000,
            availability = TrackAvailability.SubscriptionRequired,
        )
        val recommendation = TrackAggregation.aggregate(listOf(unavailable, playable)).single().recommendation
        assertEquals(MusicSource.Netease, recommendation?.track?.id?.source)
    }

    @Test fun advertisedTierBeatsAnUnconfirmedCatalogueCeilingAtTheSameTier() {
        val known = track(MusicSource.Kuwo, "晴天", quality = AudioQualityTier.High, bitrate = 320_000)
        val unknown = track(MusicSource.QQMusic, "晴天")
        val recommendation = TrackAggregation.aggregate(listOf(unknown, known)).single().recommendation
        assertEquals(MusicSource.Kuwo, recommendation?.track?.id?.source)
    }

    @Test fun sameTitleWithVeryDifferentLengthStaysSeparate() {
        val groups = TrackAggregation.aggregate(
            listOf(track(MusicSource.Netease, "Intro", duration = 30_000L), track(MusicSource.QQMusic, "Intro", duration = 240_000L)),
        )
        assertEquals(2, groups.size)
    }

    @Test fun artistOrderDoesNotCreateDuplicateRows() {
        assertEquals(
            TrackAggregation.normalizeArtist("周杰伦 / 费玉清"),
            TrackAggregation.normalizeArtist("费玉清、周杰伦"),
        )
    }

    @Test fun smallDurationDriftStillAggregates() {
        val groups = TrackAggregation.aggregate(
            listOf(track(MusicSource.Netease, "晴天", duration = 240_000L), track(MusicSource.QQMusic, "晴天", duration = 242_000L)),
        )
        assertEquals(1, groups.size)
    }
}
