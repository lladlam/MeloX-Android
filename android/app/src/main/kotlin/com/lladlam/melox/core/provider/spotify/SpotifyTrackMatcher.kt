package com.lladlam.melox.core.provider.spotify

import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.music.provider.TrackAggregation
import kotlin.math.abs

data class SpotifyMatchScore(
    val candidate: MusicTrack,
    val score: Int,
)

object SpotifyTrackMatcher {
    private const val DurationToleranceMs = 3_000L
    private const val MinimumScore = 110

    fun rank(source: MusicTrack, candidates: List<MusicTrack>): List<SpotifyMatchScore> = candidates
        .asSequence()
        .filter { it.id.source != MusicSource.Spotify }
        .mapNotNull { candidate -> score(source, candidate) }
        .filter { it.score >= MinimumScore }
        .sortedByDescending(SpotifyMatchScore::score)
        .toList()

    fun score(source: MusicTrack, candidate: MusicTrack): SpotifyMatchScore? {
        if (candidate.id.source == MusicSource.Spotify) return null
        if (TrackAggregation.normalize(source.title) != TrackAggregation.normalize(candidate.title)) return null
        val sourceArtists = source.artists.map { TrackAggregation.normalizeArtist(it.name) }.filter(String::isNotBlank).toSet()
        val candidateArtists = candidate.artists.map { TrackAggregation.normalizeArtist(it.name) }.filter(String::isNotBlank).toSet()
        if (sourceArtists.isEmpty() || candidateArtists.isEmpty() || sourceArtists.intersect(candidateArtists).isEmpty()) return null
        val sourcePrimaryArtist = source.artists.firstOrNull()?.name?.let(TrackAggregation::normalizeArtist)
        val candidatePrimaryArtist = candidate.artists.firstOrNull()?.name?.let(TrackAggregation::normalizeArtist)
        if (sourcePrimaryArtist.isNullOrBlank() || sourcePrimaryArtist != candidatePrimaryArtist) return null
        val sourceIsrc = (source.providerMetadata as? com.lladlam.melox.core.music.model.ProviderTrackMetadata.Spotify)
            ?.isrc?.uppercase()
        val candidateIsrc = (candidate.providerMetadata as? com.lladlam.melox.core.music.model.ProviderTrackMetadata.Spotify)
            ?.isrc?.uppercase()
        if (sourceIsrc != null && candidateIsrc != null && sourceIsrc != candidateIsrc) return null
        val durationDelta = if (source.durationMs != null && candidate.durationMs != null) {
            abs(source.durationMs - candidate.durationMs)
        } else null
        if (durationDelta != null && durationDelta > DurationToleranceMs) return null
        if (source.durationMs != null && candidate.durationMs == null) return null
        val availability = when (candidate.availability) {
            TrackAvailability.Playable -> 30
            TrackAvailability.Unknown -> 10
            else -> return null
        }
        val artistScore = if (sourceArtists == candidateArtists) 30 else 20
        val durationScore = durationDelta?.let { (30 - it / 100L).coerceAtLeast(0L).toInt() } ?: 0
        return SpotifyMatchScore(candidate, 40 + artistScore + durationScore + availability + sourcePriority(candidate.id.source))
    }

    private fun sourcePriority(source: MusicSource): Int = when (source) {
        MusicSource.QQMusic -> 5
        MusicSource.AppleMusic -> 4
        MusicSource.Netease -> 3
        MusicSource.Kugou -> 2
        MusicSource.Kuwo -> 2
        MusicSource.Bilibili -> 1
        MusicSource.Spotify -> 0
        MusicSource.Jellyfin -> 0
        MusicSource.Local -> 0
    }
}
