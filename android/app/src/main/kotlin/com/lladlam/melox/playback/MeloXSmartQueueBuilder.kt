package com.lladlam.melox.playback

import androidx.media3.common.MediaItem

/** Holds playlist candidates outside Media3 so the visible queue grows one match at a time. */
object MeloXSmartQueueBuilder {
    data class AnalysisSummary(val bpm: Double, val confidence: Double)
    enum class MatchReason { CompatibleTempo, HighestConfidence }
    data class Match(
        val item: MediaItem,
        val analysis: AnalysisSummary,
        val tempoDistance: Double,
        val reason: MatchReason,
    )

    private val lock = Any()
    private var candidates = mutableListOf<MediaItem>()
    private val consumedIds = linkedSetOf<String>()
    private val analyses = mutableMapOf<String, AnalysisSummary>()
    private val failedIds = mutableSetOf<String>()
    private var generation = 0L

    fun begin(items: List<MediaItem>, selectedIndex: Int): MediaItem {
        val selected = items[selectedIndex.coerceIn(items.indices)]
        synchronized(lock) {
            generation += 1L
            consumedIds.clear()
            consumedIds += selected.mediaId
            candidates = items.filterIndexed { index, _ -> index != selectedIndex }.toMutableList()
            analyses.clear()
            failedIds.clear()
        }
        return selected
    }

    fun snapshot(): CandidateSnapshot = synchronized(lock) {
        CandidateSnapshot(generation, candidates.filterNot { it.mediaId in consumedIds })
    }

    fun consume(expectedGeneration: Long, mediaId: String): MediaItem? = synchronized(lock) {
        if (generation != expectedGeneration || mediaId in consumedIds) return@synchronized null
        val index = candidates.indexOfFirst { it.mediaId == mediaId }
        if (index < 0) return@synchronized null
        consumedIds += mediaId
        candidates.removeAt(index)
    }

    fun recordAnalysis(expectedGeneration: Long, mediaId: String, analysis: MeloXAutoMixTrackAnalysis) =
        synchronized(lock) {
            if (generation == expectedGeneration) {
                analyses[mediaId] = AnalysisSummary(analysis.bpm, analysis.confidence)
            }
        }

    fun bestAnalyzedMatch(
        expectedGeneration: Long,
        sourceId: String,
    ): Match? = synchronized(lock) {
        if (generation != expectedGeneration) return@synchronized null
        val source = analyses[sourceId]
        if (source == null && sourceId !in failedIds) return@synchronized null
        val remaining = candidates.filterNot { it.mediaId in consumedIds || it.mediaId in failedIds }
        val analyzed = remaining.mapNotNull { item ->
            analyses[item.mediaId]?.let { candidate ->
                Match(
                    item = item,
                    analysis = candidate,
                    tempoDistance = source?.let { compatibleTempoDistance(it.bpm, candidate.bpm) } ?: Double.POSITIVE_INFINITY,
                    reason = MatchReason.CompatibleTempo,
                )
            }
        }
        analyzed
            .asSequence()
            .filter { it.tempoDistance <= COMPATIBLE_TEMPO_DISTANCE_BPM }
            .minByOrNull { match ->
                match.tempoDistance + (1.0 - match.analysis.confidence) * 4.0
            }
            ?: if (analyzed.size == remaining.size && remaining.isNotEmpty()) {
                analyzed.maxByOrNull { it.analysis.confidence }?.copy(
                    reason = MatchReason.HighestConfidence,
                )
            } else null
    }

    /** DJ tempo matching treats half-time and double-time estimates as equivalent. */
    internal fun compatibleTempoDistance(sourceBpm: Double, candidateBpm: Double): Double =
        listOf(candidateBpm * .5, candidateBpm, candidateBpm * 2.0)
            .minOf { adjusted -> kotlin.math.abs(sourceBpm - adjusted) }

    private const val COMPATIBLE_TEMPO_DISTANCE_BPM = 8.0

    fun firstRemaining(expectedGeneration: Long): MediaItem? = synchronized(lock) {
        if (generation != expectedGeneration) null
        else candidates.firstOrNull { it.mediaId !in consumedIds && it.mediaId !in failedIds }
    }

    fun recordFailure(expectedGeneration: Long, mediaId: String) = synchronized(lock) {
        if (generation == expectedGeneration) failedIds.add(mediaId)
    }

    fun emergencyCandidate(expectedGeneration: Long): MediaItem? = synchronized(lock) {
        if (generation != expectedGeneration) return@synchronized null
        val remaining = candidates.filterNot { it.mediaId in consumedIds || it.mediaId in failedIds }
        remaining.filter { it.mediaId in analyses }.maxByOrNull { analyses.getValue(it.mediaId).confidence }
            ?: remaining.firstOrNull()
    }

    fun reset() = synchronized(lock) {
        generation += 1L
        candidates.clear()
        consumedIds.clear()
        analyses.clear()
        failedIds.clear()
    }
}

data class CandidateSnapshot(val generation: Long, val items: List<MediaItem>)
