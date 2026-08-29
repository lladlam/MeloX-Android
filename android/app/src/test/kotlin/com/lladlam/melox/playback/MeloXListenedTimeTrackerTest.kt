package com.lladlam.melox.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class MeloXListenedTimeTrackerTest {
    @Test fun accumulatesOnlyWhilePlaying() {
        val tracker = MeloXListenedTimeTracker()
        tracker.reset(1_000L, true)
        tracker.onPlayingChanged(6_000L, false)
        assertEquals(5_000L, tracker.elapsedMs(20_000L))
        tracker.onPlayingChanged(21_000L, true)
        assertEquals(8_000L, tracker.elapsedMs(24_000L))
    }

    @Test fun seeksDoNotAffectElapsedTime() {
        val tracker = MeloXListenedTimeTracker()
        tracker.reset(0L, true)
        assertEquals(3_000L, tracker.elapsedMs(3_000L))
        assertEquals(7_000L, tracker.elapsedMs(7_000L))
    }

    @Test fun capsElapsedTimeAtDuration() {
        val tracker = MeloXListenedTimeTracker()
        tracker.reset(0L, true)
        assertEquals(5_000L, tracker.elapsedMs(9_000L, durationMs = 5_000L))
    }

    @Test fun resetStartsASeparateTrack() {
        val tracker = MeloXListenedTimeTracker()
        tracker.reset(0L, true)
        tracker.reset(10_000L, false)
        assertEquals(0L, tracker.elapsedMs(20_000L))
    }
}
