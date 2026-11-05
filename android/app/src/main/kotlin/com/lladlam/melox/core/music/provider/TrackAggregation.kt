package com.lladlam.melox.core.music.provider

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.music.model.TrackAvailability
import com.lladlam.melox.core.music.model.audioQualityForBitrate
import com.lladlam.melox.core.music.model.rank
import kotlin.math.abs

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
    /**
     * Prefer a verified playable item first; only then compare quality. Without
     * this gate, a subscription-only lossless card would hide a playable 320k
     * result that the user can actually start immediately.
     */
    val recommendation: AggregatedTrackCandidate?
        get() = candidates.maxWithOrNull(
            compareBy<AggregatedTrackCandidate> { availabilityPriority(it.track.availability) }
                .thenBy { it.totalScore },
        )
}

private fun availabilityPriority(availability: TrackAvailability): Int = when (availability) {
    TrackAvailability.Playable -> 5
    TrackAvailability.Unknown -> 4
    TrackAvailability.PreviewOnly -> 3
    TrackAvailability.LoginRequired,
    TrackAvailability.SubscriptionRequired -> 2
    TrackAvailability.RegionRestricted,
    TrackAvailability.CopyrightRestricted,
    TrackAvailability.Unavailable -> 1
}

object TrackAggregation {
    private val punctuation = Regex("[\\p{Punct}\\s]+")
    private val artistSeparators = Regex("[/,、，&｜\\s]+")
    /**
     * Providers disagree by a second or two on the same recording, but a
     * 30-second interlude and a 4-minute song with the same title must stay
     * separate rows. Ten seconds is wide enough for metadata drift and far
     * below a real alternate recording.
     */
    private const val DurationToleranceMs = 10_000L
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
        .split(artistSeparators)
        .map { it.replace(punctuation, "") }
        .filter { it.isNotBlank() && it !in setOf("feat", "ft", "featuring", "with") }
        .distinct()
        .sorted()
        .joinToString("")

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
        .flatMap { (baseKey, grouped) ->
            clusterByDuration(grouped).map { cluster ->
                val duration = cluster.mapNotNull { it.durationMs?.takeIf { ms -> ms > 0L } }
                    .takeIf { it.isNotEmpty() }
                    ?.average()
                    ?.toLong()
                val key = baseKey.copy(durationBucket = duration)
                AggregatedTrack(
                    key = key,
                    candidates = cluster.map { score(it, key) }.sortedByDescending { it.totalScore },
                )
            }
        }
        .sortedByDescending { it.recommendation?.totalScore ?: 0 }

    private fun clusterByDuration(tracks: List<MusicTrack>): List<List<MusicTrack>> {
        val clusters = mutableListOf<MutableList<MusicTrack>>()
        tracks.filter { (it.durationMs ?: 0L) > 0L }
            .sortedBy { it.durationMs ?: 0L }
            .forEach { track ->
                val duration = track.durationMs ?: return@forEach
                val cluster = clusters.firstOrNull { group ->
                    val known = group.mapNotNull { it.durationMs?.takeIf { ms -> ms > 0L } }
                    known.isNotEmpty() && abs(known.average() - duration) <= DurationToleranceMs
                }
                if (cluster == null) clusters.add(mutableListOf(track)) else cluster.add(track)
            }
        val withoutDuration = tracks.filter { (it.durationMs ?: 0L) <= 0L }
        if (withoutDuration.isNotEmpty()) {
            if (clusters.isEmpty()) clusters.add(mutableListOf())
            clusters.first().addAll(withoutDuration)
        }
        return clusters
    }

    private fun score(track: MusicTrack, key: UnifiedTrackKey): AggregatedTrackCandidate {
        val identity = 30
        val version = if (versionOf(track) == key.version) 25 else 0
        val qualityTier = track.bestQuality ?: audioQualityForBitrate(track.bestBitrate)
        val qualityScore = qualityScore(track, qualityTier)
        val availability = when (track.availability) {
            TrackAvailability.Playable -> 20
            TrackAvailability.Unknown -> 10
            TrackAvailability.PreviewOnly -> 6
            TrackAvailability.LoginRequired,
            TrackAvailability.SubscriptionRequired -> 2
            TrackAvailability.RegionRestricted,
            TrackAvailability.CopyrightRestricted,
            TrackAvailability.Unavailable -> 0
        }
        val sourceBonus = track.id.sourceId.builtIn?.let(::sourceBonus) ?: 0
        return AggregatedTrackCandidate(
            track = track,
            identityScore = identity,
            versionScore = version,
            qualityScore = qualityScore,
            availabilityScore = availability,
            totalScore = identity + version + qualityScore + availability + sourceBonus,
            reason = "版本 ${key.version} · ${track.id.sourceId.displayName} · ${qualityLabel(qualityTier)}",
        )
    }

    private fun qualityScore(track: MusicTrack, tier: AudioQualityTier?): Int {
        if (tier == null) return 0
        val bitrate = track.bestBitrate?.takeIf { it > 0 } ?: representativeBitrate(tier)
        // Tier is authoritative; bitrate only breaks ties inside the same tier.
        return tier.rank() * 10_000 + (bitrate / 1_000).coerceIn(0, 2_000)
    }

    private fun representativeBitrate(tier: AudioQualityTier): Int = when (tier) {
        AudioQualityTier.Standard -> 128_000
        AudioQualityTier.High -> 320_000
        AudioQualityTier.Lossless -> 1_000_000
        AudioQualityTier.HiResolution,
        AudioQualityTier.Immersive,
        AudioQualityTier.Master -> 1_411_200
    }

    private fun sourceBonus(source: MusicSource): Int = when (source) {
        MusicSource.AppleMusic -> 3
        MusicSource.QQMusic -> 2
        MusicSource.Netease -> 1
        MusicSource.Kugou, MusicSource.KugouLite, MusicSource.Kuwo -> 1
        else -> 0
    }

    private fun qualityLabel(tier: AudioQualityTier?): String = when (tier) {
        AudioQualityTier.Master -> "母带"
        AudioQualityTier.HiResolution -> "Hi-Res"
        AudioQualityTier.Immersive -> "沉浸声"
        AudioQualityTier.Lossless -> "无损"
        AudioQualityTier.High -> "高品质"
        AudioQualityTier.Standard -> "标准"
        null -> "音质未知"
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
