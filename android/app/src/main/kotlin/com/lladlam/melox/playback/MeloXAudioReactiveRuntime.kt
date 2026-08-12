package com.lladlam.melox.playback

import kotlin.math.abs
import kotlin.math.max

/** Lightweight process bridge from the playback analyser to Compose visuals. */
object MeloXAudioReactiveRuntime {
    @Volatile
    private var mediaId: String? = null

    @Volatile
    private var analysis: MeloXAutoMixTrackAnalysis? = null

    @Volatile
    private var positionMs: Long = 0L

    @Volatile
    private var playing: Boolean = false

    fun select(mediaId: String?) {
        if (this.mediaId == mediaId) return
        this.mediaId = mediaId
        analysis = null
        positionMs = 0L
    }

    fun publish(mediaId: String?, positionMs: Long, isPlaying: Boolean) {
        select(mediaId)
        this.positionMs = positionMs.coerceAtLeast(0L)
        playing = isPlaying
    }

    fun attach(mediaId: String, analysis: MeloXAutoMixTrackAnalysis) {
        if (this.mediaId == mediaId) this.analysis = analysis
    }

    fun clear() {
        mediaId = null
        analysis = null
        positionMs = 0L
        playing = false
    }

    fun sample(expectedMediaId: String?): MeloXAudioReactiveSample {
        if (expectedMediaId == null || expectedMediaId != mediaId) return MeloXAudioReactiveSample.Idle
        val localAnalysis = analysis ?: return MeloXAudioReactiveSample(0.18f, 0f, 0f, playing)
        val time = positionMs
        val frame = localAnalysis.frameAt(time)
        val beat = pulseAt(localAnalysis.beatTimesMs, time, 230L)
        val downbeat = pulseAt(localAnalysis.downbeatTimesMs, time, 360L)
        return MeloXAudioReactiveSample(
            energy = (frame?.energy ?: 0.18f).coerceIn(0f, 1.25f),
            beat = beat,
            downbeat = downbeat,
            isPlaying = playing,
        )
    }

    internal fun pulseAt(events: LongArray, positionMs: Long, widthMs: Long): Float {
        if (events.isEmpty() || widthMs <= 0L) return 0f
        val insertion = events.binarySearch(positionMs).let { if (it >= 0) it else -it - 1 }
        var distance = Long.MAX_VALUE
        if (insertion in events.indices) distance = abs(events[insertion] - positionMs)
        if (insertion - 1 in events.indices) distance = minOf(distance, abs(events[insertion - 1] - positionMs))
        return (1f - distance.toFloat() / max(widthMs, 1L).toFloat()).coerceIn(0f, 1f)
    }
}

data class MeloXAudioReactiveSample(
    val energy: Float,
    val beat: Float,
    val downbeat: Float,
    val isPlaying: Boolean,
) {
    companion object {
        val Idle = MeloXAudioReactiveSample(0.18f, 0f, 0f, false)
    }
}
