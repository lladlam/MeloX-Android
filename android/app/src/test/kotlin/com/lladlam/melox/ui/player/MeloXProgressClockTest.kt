package com.lladlam.melox.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class MeloXProgressClockTest {
    @Test
    fun progressAdvancesBetweenControllerPolls() {
        assertEquals(12_500L, interpolatedPlaybackPositionMs(10_000L, 1_000L, 3_500L, 60_000L))
    }

    @Test
    fun progressNeverPassesTheKnownDuration() {
        assertEquals(60_000L, interpolatedPlaybackPositionMs(59_800L, 1_000L, 5_000L, 60_000L))
    }

    @Test
    fun unknownDurationAndBackwardClockAreSafe() {
        assertEquals(15_000L, interpolatedPlaybackPositionMs(15_000L, 0L, 0L, 0L))
        assertEquals(15_000L, interpolatedPlaybackPositionMs(15_000L, 5_000L, 1_000L, 0L))
    }
}
