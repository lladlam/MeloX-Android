package com.lladlam.melox.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import com.lladlam.melox.ui.settings.MeloXSettingsPreferences

/** Owns the Android DSP effect attached to the active ExoPlayer audio session. */
class MeloXEqualizerController(private val context: Context) {
    private var effect: Equalizer? = null
    private var sessionId = 0

    fun attach(newSessionId: Int) {
        if (newSessionId <= 0 || newSessionId == sessionId) return
        release()
        sessionId = newSessionId
        effect = runCatching { Equalizer(0, newSessionId) }
            .onFailure { Log.w("MeloXPlayback", "Equalizer unavailable", it) }
            .getOrNull()
        applySettings()
    }

    fun applySettings() {
        val equalizer = effect ?: return
        runCatching {
            val enabled = MeloXSettingsPreferences.boolean(context, "equalizer_enabled", false)
            val preset = MeloXSettingsPreferences.string(context, "equalizer_preset", "Flat")
            val preamp = MeloXSettingsPreferences.int(context, "equalizer_preamp_db", 0).coerceIn(-6, 6)
            val gains = if (preset == "Custom") {
                IntArray(5) { index ->
                    MeloXSettingsPreferences.int(context, "equalizer_custom_band_$index", 0).coerceIn(-6, 6)
                }
            } else {
                PRESETS[preset] ?: PRESETS.getValue("Flat")
            }
            val range = equalizer.bandLevelRange
            repeat(equalizer.numberOfBands.toInt()) { index ->
                val source = (index * gains.size / equalizer.numberOfBands.toInt()).coerceIn(gains.indices)
                val level = ((gains[source] + preamp) * 100).coerceIn(range[0].toInt(), range[1].toInt())
                equalizer.setBandLevel(index.toShort(), level.toShort())
            }
            equalizer.enabled = enabled
        }.onFailure {
            Log.w("MeloXPlayback", "Equalizer update failed; releasing effect", it)
            release()
        }
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
        sessionId = 0
    }

    companion object {
        val PRESETS = linkedMapOf(
            "Flat" to intArrayOf(0, 0, 0, 0, 0),
            "Bass" to intArrayOf(5, 4, 2, 0, -1),
            "Vocal" to intArrayOf(-2, 0, 3, 4, 2),
            "Treble" to intArrayOf(-2, -1, 1, 4, 5),
            "Electronic" to intArrayOf(4, 2, 0, 2, 4),
            "Custom" to intArrayOf(0, 0, 0, 0, 0),
        )
    }
}
