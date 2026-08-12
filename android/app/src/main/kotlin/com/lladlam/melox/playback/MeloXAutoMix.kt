package com.lladlam.melox.playback

import android.content.Context
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class MeloXAutoMixMode { Smart, Fixed }
enum class MeloXAutoMixFadeCurve { EqualPower, Smooth, Linear }
enum class MeloXAutoMixFallback { Crossfade, ShortCrossfade, Normal }

data class MeloXAutoMixSettings(
    val mode: MeloXAutoMixMode = MeloXAutoMixMode.Smart,
    val transitionBars: Int = 8,
    val tailCutBars: Int = 4,
    val fixedDurationMs: Long = 8_000L,
    val preloadLeadMs: Long = 90_000L,
    val fadeCurve: MeloXAutoMixFadeCurve = MeloXAutoMixFadeCurve.EqualPower,
    val tempoMatching: Boolean = true,
    val maxTempoAdjustment: Double = 0.05,
    val skipQuietOpening: Boolean = true,
    val minimumConfidence: Double = 0.42,
    val analyzeStreaming: Boolean = true,
    val fallback: MeloXAutoMixFallback = MeloXAutoMixFallback.Crossfade,
) {
    companion object {
        fun read(context: Context): MeloXAutoMixSettings {
            val prefs = MeloXPlaybackModePreferences.preferences(context)
            fun <T : Enum<T>> enumValue(key: String, fallback: T, values: Array<T>): T {
                val stored = prefs.getString(key, fallback.name)
                return values.firstOrNull { it.name == stored } ?: fallback
            }
            return MeloXAutoMixSettings(
                mode = enumValue("automix_mode", MeloXAutoMixMode.Smart, MeloXAutoMixMode.entries.toTypedArray()),
                transitionBars = prefs.getInt("automix_transition_bars", 8).coerceIn(4, 16),
                tailCutBars = prefs.getInt("automix_tail_cut_bars", 4).coerceIn(0, 8),
                fixedDurationMs = prefs.getLong("automix_fixed_duration_ms", 8_000L).coerceIn(3_000L, 20_000L),
                preloadLeadMs = prefs.getLong("automix_preload_lead_ms", 90_000L).coerceIn(30_000L, 180_000L),
                fadeCurve = enumValue("automix_fade_curve", MeloXAutoMixFadeCurve.EqualPower, MeloXAutoMixFadeCurve.entries.toTypedArray()),
                tempoMatching = prefs.getBoolean("automix_tempo_matching", true),
                maxTempoAdjustment = prefs.getFloat("automix_max_tempo_adjustment", 0.05f).toDouble().coerceIn(0.01, 0.08),
                skipQuietOpening = prefs.getBoolean("automix_skip_quiet_opening", true),
                minimumConfidence = prefs.getFloat("automix_minimum_confidence", 0.42f).toDouble().coerceIn(0.2, 0.8),
                analyzeStreaming = prefs.getBoolean("automix_analyze_streaming", true),
                fallback = enumValue("automix_fallback", MeloXAutoMixFallback.Crossfade, MeloXAutoMixFallback.entries.toTypedArray()),
            )
        }
    }
}

data class MeloXAutoMixAnalysis(
    val bpm: Double,
    val confidence: Double,
    val firstAudibleMs: Long = 0L,
    val lastAudibleMs: Long? = null,
)

data class MeloXAutoMixPlan(
    val durationMs: Long,
    val incomingStartMs: Long,
    val outgoingStartMs: Long = 0L,
    val outgoingEndOffsetMs: Long = 0L,
    val outgoingStartRate: Float = 1f,
    val outgoingEndRate: Float = 1f,
    val incomingStartRate: Float = 1f,
    val incomingEndRate: Float = 1f,
    val usedSmartAnalysis: Boolean = false,
) {
    val performsTransition: Boolean get() = durationMs > 0L
}

/** Pure transition planner ported from MeloX's AutoMixTransitionPlanner. */
object MeloXAutoMixPlanner {
    fun plan(
        settings: MeloXAutoMixSettings,
        outgoingRemainingMs: Long,
        outgoing: MeloXAutoMixAnalysis? = null,
        incoming: MeloXAutoMixAnalysis? = null,
    ): MeloXAutoMixPlan {
        if (settings.mode == MeloXAutoMixMode.Fixed) {
            return fixed(settings.fixedDurationMs, outgoingRemainingMs)
        }
        val confident = outgoing != null && incoming != null &&
            outgoing.confidence >= settings.minimumConfidence &&
            incoming.confidence >= settings.minimumConfidence &&
            outgoing.bpm > 0.0 && incoming.bpm > 0.0
        if (!confident) return fallback(settings, outgoingRemainingMs)

        val beatMs = 60_000.0 / outgoing.bpm
        val requestedDuration = (settings.transitionBars * 4 * beatMs).toLong()
        val available = outgoingRemainingMs - HANDOFF_GUARD_MS
        if (available < MIN_DURATION_MS) return MeloXAutoMixPlan(0L, 0L)
        val duration = requestedDuration.coerceIn(MIN_DURATION_MS, available)
        if (duration < MIN_DURATION_MS) return MeloXAutoMixPlan(0L, 0L)

        val incomingStart = if (settings.skipQuietOpening) incoming.firstAudibleMs.coerceAtLeast(0L) else 0L
        val rates = if (settings.tempoMatching) tempoRates(outgoing.bpm, incoming.bpm, settings.maxTempoAdjustment) else 1f to 1f
        return MeloXAutoMixPlan(
            durationMs = duration,
            outgoingStartMs = 0L,
            incomingStartMs = incomingStart,
            outgoingStartRate = 1f,
            outgoingEndRate = rates.first,
            incomingStartRate = rates.second,
            incomingEndRate = 1f,
            usedSmartAnalysis = true,
        )
    }

    private fun fallback(settings: MeloXAutoMixSettings, remainingMs: Long): MeloXAutoMixPlan = when (settings.fallback) {
        MeloXAutoMixFallback.Crossfade -> fallbackCrossfade(settings.fixedDurationMs, settings.tailCutBars, remainingMs)
        MeloXAutoMixFallback.ShortCrossfade -> fallbackCrossfade(3_000L, settings.tailCutBars, remainingMs)
        MeloXAutoMixFallback.Normal -> MeloXAutoMixPlan(0L, 0L)
    }

    private fun fixed(requestedMs: Long, remainingMs: Long): MeloXAutoMixPlan {
        val duration = minOf(requestedMs, remainingMs)
        return if (duration >= MIN_DURATION_MS) MeloXAutoMixPlan(duration, 0L) else MeloXAutoMixPlan(0L, 0L)
    }

    private fun fallbackCrossfade(requestedMs: Long, tailCutBars: Int, remainingMs: Long): MeloXAutoMixPlan {
        val tailCutMs = tailCutBars.coerceAtLeast(0) * 4 * 500L
        val available = remainingMs - tailCutMs
        val duration = minOf(requestedMs, available)
        return if (duration >= MIN_DURATION_MS) {
            MeloXAutoMixPlan(durationMs = duration, incomingStartMs = 0L, outgoingEndOffsetMs = tailCutMs)
        } else {
            MeloXAutoMixPlan(0L, 0L)
        }
    }

    private fun tempoRates(outgoingBpm: Double, incomingBpm: Double, maxAdjustment: Double): Pair<Float, Float> {
        val candidates = listOf(incomingBpm / 2.0, incomingBpm, incomingBpm * 2.0)
        val alignedIncoming = candidates.minByOrNull { kotlin.math.abs(it - outgoingBpm) } ?: incomingBpm
        val shared = (outgoingBpm + alignedIncoming) / 2.0
        val outgoingRate = (shared / outgoingBpm).coerceIn(1.0 - maxAdjustment, 1.0 + maxAdjustment)
        val incomingRate = (shared / alignedIncoming).coerceIn(1.0 - maxAdjustment, 1.0 + maxAdjustment)
        return outgoingRate.toFloat() to incomingRate.toFloat()
    }

    const val MIN_DURATION_MS = 1_500L
    const val HANDOFF_GUARD_MS = 700L
}

object MeloXAutoMixEnvelope {
    data class Gains(val outgoing: Float, val incoming: Float)

    fun gains(progress: Double, curve: MeloXAutoMixFadeCurve): Gains {
        val p = progress.coerceIn(0.0, 1.0)
        val smooth = p * p * (3.0 - 2.0 * p)
        return when (curve) {
            MeloXAutoMixFadeCurve.EqualPower -> Gains(
                outgoing = cos(smooth * PI / 2.0).toFloat(),
                incoming = sin(smooth * PI / 2.0).toFloat(),
            )
            MeloXAutoMixFadeCurve.Smooth -> Gains((1.0 - smooth).toFloat(), smooth.toFloat())
            MeloXAutoMixFadeCurve.Linear -> Gains((1.0 - p).toFloat(), p.toFloat())
        }
    }

    fun rate(start: Float, end: Float, progress: Double): Float {
        val p = progress.coerceIn(0.0, 1.0)
        val smooth = p * p * (3.0 - 2.0 * p)
        return (start.toDouble() + (end - start).toDouble() * smooth).toFloat()
    }
}
