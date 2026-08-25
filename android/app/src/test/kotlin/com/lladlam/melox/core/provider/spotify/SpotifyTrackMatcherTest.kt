package com.lladlam.melox.core.provider.spotify

import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.TrackAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyTrackMatcherTest {
    private fun track(
        source: MusicSource,
        title: String = "A Song",
        artist: String = "The Artist",
        duration: Long? = 200_000L,
        availability: TrackAvailability = TrackAvailability.Playable,
    ) = MusicTrack(
        MusicResourceId(source, "$source-$title-$artist"),
        title,
        listOf(MusicArtistRef(name = artist)),
        durationMs = duration,
        availability = availability,
    )

    @Test
    fun exactPlayableMatchScoresAndWins() {
        val source = track(MusicSource.Spotify)
        val exact = track(MusicSource.QQMusic)
        val close = track(MusicSource.Netease, duration = 201_500L)
        val ranked = SpotifyTrackMatcher.rank(source, listOf(close, exact))
        assertEquals(exact.id, ranked.first().candidate.id)
        assertTrue(ranked.first().score > ranked.last().score)
    }

    @Test
    fun exactMatchesUseAggregationSourcePriority() {
        val source = track(MusicSource.Spotify)
        val netease = track(MusicSource.Netease)
        val qqMusic = track(MusicSource.QQMusic)
        val ranked = SpotifyTrackMatcher.rank(source, listOf(netease, qqMusic))
        assertEquals(qqMusic.id, ranked.first().candidate.id)
    }

    @Test fun rejectsWrongTitle() = assertNull(
        SpotifyTrackMatcher.score(track(MusicSource.Spotify), track(MusicSource.Netease, title = "Another Song")),
    )

    @Test fun rejectsWrongArtist() = assertNull(
        SpotifyTrackMatcher.score(track(MusicSource.Spotify), track(MusicSource.Netease, artist = "Wrong Artist")),
    )

    @Test
    fun rejectsCandidateThatOnlySharesAFeaturedArtist() {
        val source = track(MusicSource.Spotify).copy(
            artists = listOf(MusicArtistRef(name = "The Artist"), MusicArtistRef(name = "Guest")),
        )
        val candidate = track(MusicSource.Netease, artist = "Guest")
        assertNull(SpotifyTrackMatcher.score(source, candidate))
    }

    @Test fun rejectsLargeDurationDifference() = assertNull(
        SpotifyTrackMatcher.score(track(MusicSource.Spotify), track(MusicSource.Netease, duration = 210_000L)),
    )

    @Test fun excludesSpotifyToPreventRecursion() = assertTrue(
        SpotifyTrackMatcher.rank(track(MusicSource.Spotify), listOf(track(MusicSource.Spotify))).isEmpty(),
    )

    @Test
    fun rankingRejectsLowConfidenceNearDurationBoundary() {
        val source = track(MusicSource.Spotify)
        val candidate = track(MusicSource.Netease, duration = 202_900L)
        assertTrue(SpotifyTrackMatcher.rank(source, listOf(candidate)).isEmpty())
    }
}
