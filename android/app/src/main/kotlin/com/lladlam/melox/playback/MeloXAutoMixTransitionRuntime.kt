package com.lladlam.melox.playback

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Process-local bridge from the dual-deck service to the Compose player. */
object MeloXAutoMixTransitionRuntime {
    private const val EXIT_DURATION_MS = 650L
    private val handler = Handler(Looper.getMainLooper())
    private val clearAfterHandoff = Runnable(::clearNow)

    var active by mutableStateOf(false)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var handedOff by mutableStateOf(false)
        private set
    var incomingPositionMs by mutableStateOf(0L)
        private set
    var incomingDurationMs by mutableStateOf(0L)
        private set
    var outgoingArtworkUrl by mutableStateOf<String?>(null)
        private set
    var incomingArtworkUrl by mutableStateOf<String?>(null)
        private set
    var incomingTitle by mutableStateOf("")
        private set
    var incomingArtist by mutableStateOf("")
        private set

    fun begin(outgoing: androidx.media3.common.MediaItem?, incoming: androidx.media3.common.MediaItem?) {
        handler.removeCallbacks(clearAfterHandoff)
        outgoingArtworkUrl = outgoing?.mediaMetadata?.artworkUri?.toString()
            ?: outgoing?.mediaMetadata?.extras?.getString(PlaybackTrackIdentity.ArtworkExtra)
        incomingArtworkUrl = incoming?.mediaMetadata?.artworkUri?.toString()
            ?: incoming?.mediaMetadata?.extras?.getString(PlaybackTrackIdentity.ArtworkExtra)
        incomingTitle = incoming?.mediaMetadata?.title?.toString().orEmpty()
            .ifBlank { incoming?.mediaMetadata?.extras?.getString(PlaybackTrackIdentity.TitleExtra).orEmpty() }
        incomingArtist = incoming?.mediaMetadata?.artist?.toString().orEmpty()
            .ifBlank { incoming?.mediaMetadata?.extras?.getString(PlaybackTrackIdentity.ArtistExtra).orEmpty() }
        progress = 0f
        handedOff = false
        incomingPositionMs = 0L
        incomingDurationMs = 0L
        active = true
    }

    fun update(value: Double, positionMs: Long, durationMs: Long) {
        progress = value.toFloat().coerceIn(0f, 1f)
        incomingPositionMs = positionMs.coerceAtLeast(0L)
        incomingDurationMs = durationMs.coerceAtLeast(0L)
    }

    fun handoff() {
        progress = 1f
        handedOff = true
        handler.removeCallbacks(clearAfterHandoff)
        handler.postDelayed(clearAfterHandoff, EXIT_DURATION_MS)
    }

    fun clear() {
        handler.removeCallbacks(clearAfterHandoff)
        clearNow()
    }

    private fun clearNow() {
        active = false
        progress = 0f
        handedOff = false
        incomingPositionMs = 0L
        incomingDurationMs = 0L
        outgoingArtworkUrl = null
        incomingArtworkUrl = null
        incomingTitle = ""
        incomingArtist = ""
    }
}
