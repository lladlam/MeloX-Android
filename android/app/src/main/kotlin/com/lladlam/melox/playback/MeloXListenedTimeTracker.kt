package com.lladlam.melox.playback

/** Tracks actual playing time using a monotonic clock; seeks never affect the total. */
internal class MeloXListenedTimeTracker {
    private var listenedMs = 0L
    private var playingSinceMs: Long? = null

    fun reset(nowMs: Long, isPlaying: Boolean) {
        listenedMs = 0L
        playingSinceMs = nowMs.takeIf { isPlaying }
    }

    fun onPlayingChanged(nowMs: Long, isPlaying: Boolean) {
        settle(nowMs)
        playingSinceMs = nowMs.takeIf { isPlaying }
    }

    fun elapsedMs(nowMs: Long, durationMs: Long? = null): Long {
        val current = listenedMs + playingSinceMs?.let { (nowMs - it).coerceAtLeast(0L) }.orZero()
        return durationMs?.takeIf { it > 0L }?.let(current::coerceAtMost) ?: current
    }

    private fun settle(nowMs: Long) {
        playingSinceMs?.let { listenedMs += (nowMs - it).coerceAtLeast(0L) }
        playingSinceMs = null
    }
}

private fun Long?.orZero(): Long = this ?: 0L
