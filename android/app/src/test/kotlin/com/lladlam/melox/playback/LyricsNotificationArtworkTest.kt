package com.lladlam.melox.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsNotificationArtworkTest {
    @Test fun samplesLargeAndNonSquareCovers() {
        assertEquals(1, notificationArtworkSampleSize(256, 256))
        assertEquals(2, notificationArtworkSampleSize(512, 300))
        assertEquals(8, notificationArtworkSampleSize(2000, 1200))
        assertEquals(16, notificationArtworkSampleSize(4096, 300))
    }
}
