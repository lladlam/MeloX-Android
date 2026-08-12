package com.lladlam.melox.playback

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXAutoMixTest {
    @Test
    fun equalPowerEnvelopeKeepsMidpointPowerConstant() {
        val gains = MeloXAutoMixEnvelope.gains(.5, MeloXAutoMixFadeCurve.EqualPower)
        assertEquals((sqrt(.5)).toFloat(), gains.outgoing, .001f)
        assertEquals((sqrt(.5)).toFloat(), gains.incoming, .001f)
    }

    @Test
    fun fallbackCrossfadeHonorsTailCut() {
        val plan = MeloXAutoMixPlanner.plan(
            settings = MeloXAutoMixSettings(
                fallback = MeloXAutoMixFallback.Crossfade,
                fixedDurationMs = 8_000L,
                tailCutBars = 4,
            ),
            outgoingRemainingMs = 20_000L,
        )
        assertEquals(8_000L, plan.durationMs)
        assertEquals(8_000L, plan.outgoingEndOffsetMs)
    }

    @Test
    fun fixedCrossfadeStartsAtRequestedRemainingTime() {
        val plan = MeloXAutoMixPlanner.plan(
            settings = MeloXAutoMixSettings(mode = MeloXAutoMixMode.Fixed, fixedDurationMs = 8_000L),
            outgoingRemainingMs = 7_980L,
        )
        assertEquals(7_980L, plan.durationMs)
        assertTrue(7_980L <= plan.durationMs + plan.outgoingEndOffsetMs)
    }

    @Test
    fun smartScorerProducesPhraseAlignedBoundedPlan() {
        val outgoing = analysis(durationMs = 180_000L, bpm = 120.0, rising = false)
        val incoming = analysis(durationMs = 180_000L, bpm = 122.0, rising = true)
        val plan = MeloXAutoMixTransitionScorer.plan(MeloXAutoMixSettings(), outgoing, incoming)

        requireNotNull(plan)
        assertTrue(plan.usedSmartAnalysis)
        assertTrue(plan.durationMs in 3_000L..32_000L)
        assertTrue(plan.outgoingStartMs > 90_000L)
        assertTrue(plan.incomingStartMs in 0L..48_000L)
        assertTrue(plan.outgoingStartRate in .92f..1.08f)
        assertTrue(plan.incomingStartRate in .92f..1.08f)
    }

    @Test
    fun audioReactivePulsePeaksOnBeatAndFallsOutsideWindow() {
        val beats = longArrayOf(1_000L, 2_000L)
        assertEquals(1f, MeloXAudioReactiveRuntime.pulseAt(beats, 1_000L, 200L), .001f)
        assertEquals(.5f, MeloXAudioReactiveRuntime.pulseAt(beats, 1_100L, 200L), .001f)
        assertEquals(0f, MeloXAudioReactiveRuntime.pulseAt(beats, 1_300L, 200L), .001f)
    }

    @Test
    fun audioReactivePulseUsesNearestEventOnEitherSide() {
        val beats = longArrayOf(1_000L, 2_000L)
        assertEquals(.75f, MeloXAudioReactiveRuntime.pulseAt(beats, 1_950L, 200L), .001f)
        assertEquals(0f, MeloXAudioReactiveRuntime.pulseAt(longArrayOf(), 1_000L, 200L), .001f)
    }

    @Test
    fun deckEqualizerPolicySkipsUnstableXiaomiFamilyEffects() {
        assertTrue(
            !MeloXAutoMixEqualizerEnvelope.supportsDeckEqualizers(
                manufacturer = "Xiaomi",
                brand = "REDMI",
                userEqualizerEnabled = false,
            ),
        )
        assertTrue(
            !MeloXAutoMixEqualizerEnvelope.supportsDeckEqualizers(
                manufacturer = "POCO",
                brand = "POCO",
                userEqualizerEnabled = false,
            ),
        )
    }

    @Test
    fun deckEqualizerPolicyAllowsKnownSafeVendorOnlyWithoutUserEq() {
        assertTrue(
            MeloXAutoMixEqualizerEnvelope.supportsDeckEqualizers(
                manufacturer = "samsung",
                brand = "samsung",
                userEqualizerEnabled = false,
            ),
        )
        assertTrue(
            !MeloXAutoMixEqualizerEnvelope.supportsDeckEqualizers(
                manufacturer = "samsung",
                brand = "samsung",
                userEqualizerEnabled = true,
            ),
        )
    }

    private fun analysis(durationMs: Long, bpm: Double, rising: Boolean): MeloXAutoMixTrackAnalysis {
        val frames = (0L..durationMs step 250L).map { time ->
            val normalized = time.toFloat() / durationMs
            val energy = if (rising) .22f + normalized * .48f else .70f - normalized * .38f
            MeloXAutoMixFrame(
                timeMs = time,
                energy = energy,
                lowRatio = .32f,
                midRatio = .48f,
                highRatio = .20f,
                novelty = if (time % 2_000L == 0L) .85f else .10f,
                onset = if (time % 500L == 0L) .76f else .08f,
            )
        }
        val beats = (0L..durationMs step 500L).toList().toLongArray()
        return MeloXAutoMixTrackAnalysis(
            bpm = bpm,
            confidence = .82,
            firstAudibleMs = 0L,
            lastAudibleMs = durationMs,
            beatTimesMs = beats,
            downbeatTimesMs = beats.filterIndexed { index, _ -> index % 4 == 0 }.toLongArray(),
            phraseBoundariesMs = beats.filterIndexed { index, _ -> index % 16 == 0 }.toLongArray(),
            frames = frames,
        )
    }
}
