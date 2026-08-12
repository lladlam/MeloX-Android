package com.lladlam.melox.playback

import android.media.audiofx.Equalizer
import kotlin.math.roundToInt

/**
 * Swaps sub/bass energy between decks around the transition midpoint. This is
 * deliberately independent from the user's graphic EQ: only bands centred at
 * or below 300 Hz are touched and every change is restored on hand-off.
 */
class MeloXAutoMixEqualizerEnvelope {
    private var outgoing: DeckEqualizer? = null
    private var incoming: DeckEqualizer? = null

    fun attach(outgoingSessionId: Int, incomingSessionId: Int) {
        release()
        outgoing = DeckEqualizer(outgoingSessionId)
        incoming = DeckEqualizer(incomingSessionId)
    }

    fun apply(progress: Double) {
        val p = progress.coerceIn(0.0, 1.0)
        val outgoingAmount = smoothStep((p - .24) / .38)
        val incomingAmount = 1.0 - smoothStep((p - .38) / .38)
        outgoing?.setBassCut(-12.0 * outgoingAmount)
        incoming?.setBassCut(-12.0 * incomingAmount)
    }

    fun release() {
        outgoing?.release()
        incoming?.release()
        outgoing = null
        incoming = null
    }

    private class DeckEqualizer(audioSessionId: Int) {
        private val equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        private val bassBands: List<Short> = equalizer?.let { effect ->
            (0 until effect.numberOfBands.toInt()).map { it.toShort() }.filter { band ->
                val range = effect.getBandFreqRange(band)
                val centreMilliHertz = (range[0].toLong() + range[1].toLong()) / 2L
                centreMilliHertz <= 300_000L
            }
        }.orEmpty()

        init {
            runCatching { equalizer?.enabled = true }
        }

        fun setBassCut(decibels: Double) {
            val effect = equalizer ?: return
            val range = effect.bandLevelRange
            val millibels = (decibels * 100.0).roundToInt()
                .coerceIn(range[0].toInt(), minOf(0, range[1].toInt()))
                .toShort()
            bassBands.forEach { band -> runCatching { effect.setBandLevel(band, millibels) } }
        }

        fun release() {
            runCatching {
                bassBands.forEach { equalizer?.setBandLevel(it, 0) }
                equalizer?.enabled = false
                equalizer?.release()
            }
        }
    }

    private fun smoothStep(value: Double): Double {
        val p = value.coerceIn(0.0, 1.0)
        return p * p * (3.0 - 2.0 * p)
    }
}
