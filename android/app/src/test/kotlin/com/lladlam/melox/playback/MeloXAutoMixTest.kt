package com.lladlam.melox.playback

import androidx.media3.common.Player
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXAutoMixTest {
    @Test
    fun periodicOnsetsProduceConfidentTempo() {
        val frameRate = 21.533203125
        val signal = List(640) { index ->
            when (index % 11) {
                0 -> 1.0
                1, 10 -> 0.45
                else -> 0.03
            }
        }

        val (bpm, confidence) = MeloXAutoMixAudioAnalyzer.estimateTempoSignal(signal, frameRate)

        assertTrue(bpm in 110.0..125.0)
        assertTrue("confidence=$confidence", confidence > .42)
    }

    @Test
    fun nonPeriodicActivityDoesNotGainConfidenceFromDensityAlone() {
        val frameRate = 21.533203125
        var state = 0x12345678
        val signal = List(640) {
            state = state * 1103515245 + 12345
            ((state ushr 8) and 0xffff) / 65535.0
        }

        val (_, confidence) = MeloXAutoMixAudioAnalyzer.estimateTempoSignal(signal, frameRate)

        assertTrue("confidence=$confidence", confidence < .42)
    }

    @Test
    fun transportCommandsHaveDistinctTransitionBehavior() {
        assertEquals(
            MeloXAutoMixTransportAction.PauseAndCancel,
            transportAction(Player.COMMAND_PLAY_PAUSE),
        )
        assertEquals(
            MeloXAutoMixTransportAction.CancelThenAllow,
            transportAction(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
        )
        assertEquals(
            MeloXAutoMixTransportAction.CancelThenAllow,
            transportAction(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
        )
        assertEquals(
            MeloXAutoMixTransportAction.ContinueOnIncoming,
            transportAction(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
        )
    }

    @Test
    fun nextBeforeTransitionStartsCancelsPreparedDeckAndUsesNormalSkip() {
        assertEquals(
            MeloXAutoMixTransportAction.CancelThenAllow,
            MeloXAutoMixTransportPolicy.action(
                command = Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                hasPreparedMix = true,
                transitionStarted = false,
                playWhenReady = true,
            ),
        )
    }

    private fun transportAction(command: Int) = MeloXAutoMixTransportPolicy.action(
        command = command,
        hasPreparedMix = true,
        transitionStarted = true,
        playWhenReady = true,
    )

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
