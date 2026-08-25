package com.lladlam.melox.core.music.provider

import com.lladlam.melox.core.music.model.MusicTrack
import kotlin.math.abs
import kotlin.math.max

enum class TrackVersionKind { Studio, Live, Remix, Dj, Instrumental, Cover, Acoustic, Unknown }

data class UnifiedTrackKey(
    val title: String,
    val artist: String,
    val durationBucket: Long?,
    val version: TrackVersionKind,
)

data class AggregatedTrackCandidate(
    val track: MusicTrack,
    val identityScore: Int,
    val versionScore: Int,
    val qualityScore: Int,
    val availabilityScore: Int,
    val totalScore: Int,
    val reason: String,
)

data class AggregatedTrack(
    val key: UnifiedTrackKey,
    val candidates: List<AggregatedTrackCandidate>,
) {
    val recommendation: AggregatedTrackCandidate? get() = candidates.maxByOrNull { it.totalScore }
}

object TrackAggregation {
    private val punctuation = Regex("[\\p{Punct}\\s]+")
    private val artistSeparators = Regex("[/,、，&｜\\s]+")
    private val versionTokens = listOf(
        TrackVersionKind.Live to listOf("live", "现场", "演唱会", "巡演"),
        TrackVersionKind.Remix to listOf("remix", "混音", "重混"),
        TrackVersionKind.Dj to listOf("dj", "club", "慢摇"),
        TrackVersionKind.Instrumental to listOf("instrumental", "伴奏", "纯音乐", "karaoke"),
        TrackVersionKind.Cover to listOf("cover", "翻唱"),
        TrackVersionKind.Acoustic to listOf("acoustic", "不插电", "木吉他"),
    )

    fun normalize(value: String): String = value.lowercase().replace(punctuation, "")

    fun normalizeArtist(value: String): String = value.lowercase()
        .replace(artistSeparators, "")
        .replace(punctuation, "")

    fun versionOf(track: MusicTrack): TrackVersionKind {
        val value = normalize(listOf(track.title, track.album?.name.orEmpty()).joinToString(" "))
        return versionTokens.firstOrNull { (_, tokens) -> tokens.any { value.contains(normalize(it)) } }?.first
            ?: TrackVersionKind.Studio
    }

    fun keyOf(track: MusicTrack): UnifiedTrackKey = UnifiedTrackKey(
        title = normalize(track.title),
        artist = normalizeArtist(track.artistText),
        durationBucket = null,
        version = versionOf(track),
    )

    fun aggregate(tracks: List<MusicTrack>): List<AggregatedTrack> = tracks
        .groupBy(::keyOf)
        .map { (key, grouped) ->
            AggregatedTrack(
                key = key,
                candidates = grouped.map { score(it, key) }.sortedByDescending { it.totalScore },
            )
        }
        .sortedByDescending { it.recommendation?.totalScore ?: 0 }

    private fun score(track: MusicTrack, key: UnifiedTrackKey): AggregatedTrackCandidate {
        val identity = 30
        val version = if (versionOf(track) == key.version) 25 else 0
        val quality = when (track.availability) {
            com.lladlam.melox.core.music.model.TrackAvailability.Playable -> 20
            com.lladlam.melox.core.music.model.TrackAvailability.PreviewOnly -> 6
            com.lladlam.melox.core.music.model.TrackAvailability.LoginRequired -> 2
            else -> 0
        }
        val sourceBonus = when (track.id.source) {
            com.lladlam.melox.core.music.model.MusicSource.QQMusic -> 5
            com.lladlam.melox.core.music.model.MusicSource.Netease -> 3
            com.lladlam.melox.core.music.model.MusicSource.Kugou -> 2
            com.lladlam.melox.core.music.model.MusicSource.AppleMusic -> 4
            com.lladlam.melox.core.music.model.MusicSource.Bilibili -> 1
            // Spotify is playable only after a strict cross-provider match. A
            // native playable result must remain the aggregation recommendation.
            com.lladlam.melox.core.music.model.MusicSource.Spotify -> -1
        }
        return AggregatedTrackCandidate(track, identity, version, quality, sourceBonus, identity + version + quality + sourceBonus, "版本 ${key.version} · ${track.id.source.displayName}")
    }

    fun similarity(left: MusicTrack, right: MusicTrack): Float {
        if (versionOf(left) != versionOf(right)) return 0f
        var score = 0f
        if (normalize(left.title) == normalize(right.title)) score += .45f
        if (normalizeArtist(left.artistText) == normalizeArtist(right.artistText)) score += .35f
        val durationDelta = abs((left.durationMs ?: 0L) - (right.durationMs ?: 0L))
        if (durationDelta <= 2_000L) score += .20f
        return score.coerceIn(0f, 1f)
    }
}
