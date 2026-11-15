package com.lladlam.melox.core.provider.kugou

import com.lladlam.melox.core.music.model.AudioQualityTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KugouAnonymousPlaybackTest {
    @Test
    fun publicTrackerTriesTheBestAnonymousVariantFirst() {
        assertEquals(listOf("128"), kugouAnonymousQualityCandidates(AudioQualityTier.Standard))
        assertEquals(listOf("hq", "128"), kugouAnonymousQualityCandidates(AudioQualityTier.High))
        assertEquals(
            listOf("sq", "hq", "128"),
            kugouAnonymousQualityCandidates(AudioQualityTier.Lossless),
        )
        assertEquals(
            listOf("sq", "hq", "128"),
            kugouAnonymousQualityCandidates(AudioQualityTier.HiResolution),
        )
    }

    @Test
    fun fullLengthTrackPassesThePreviewGuard() {
        // 190 seconds at 128 kbps.
        assertTrue(kugouAnonymousLooksComplete(fileSize = 3_041_303L, bitRate = 128_000, durationMs = 190_000L))
    }

    @Test
    fun thirtySecondTrialIsRejected() {
        // A 30-second trial of a 190-second track.
        assertFalse(kugouAnonymousLooksComplete(fileSize = 480_000L, bitRate = 128_000, durationMs = 190_000L))
    }

    @Test
    fun missingMetadataDoesNotBlockFreeTracks() {
        assertTrue(kugouAnonymousLooksComplete(fileSize = null, bitRate = null, durationMs = 190_000L))
        assertTrue(kugouAnonymousLooksComplete(fileSize = 3_041_303L, bitRate = null, durationMs = null))
    }

    @Test
    fun reportedTierFollowsTheTrackerResponse() {
        assertEquals(
            AudioQualityTier.Lossless,
            kugouAnonymousActualTier(bitRate = 0, format = "flac"),
        )
        assertEquals(
            AudioQualityTier.High,
            kugouAnonymousActualTier(bitRate = 320_000, format = "mp3"),
        )
        assertEquals(
            AudioQualityTier.Standard,
            kugouAnonymousActualTier(bitRate = 128_000, format = "mp3"),
        )
    }
}
