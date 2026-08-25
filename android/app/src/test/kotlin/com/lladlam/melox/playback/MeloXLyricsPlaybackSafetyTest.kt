package com.lladlam.melox.playback

import com.lladlam.melox.platform.floating.shouldClearLegacyNeteaseLyrics
import com.lladlam.melox.platform.lyricon.lyriconReloadKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXLyricsPlaybackSafetyTest {
    @Test
    fun queueDurationExtraHasStableKeyAndSafeLegacyFallback() {
        assertEquals("melox.track.duration_ms", PlaybackTrackIdentity.DurationMsExtra)
        assertEquals(180_000L, normalizedQueueDurationMs(180_000L))
        assertEquals(0L, normalizedQueueDurationMs(null))
        assertEquals(0L, normalizedQueueDurationMs(-1L))
    }

    @Test
    fun lyriconReloadsWhenDurationBecomesKnownOrAutomaticModeChanges() {
        val unknown = lyriconReloadKey("melox:spotify:id", "Title", "Artist", 0L, true)
        val known = lyriconReloadKey("melox:spotify:id", "Title", "Artist", 180_000L, true)
        val manual = lyriconReloadKey("melox:spotify:id", "Title", "Artist", 180_000L, false)

        assertNotEquals(unknown, known)
        assertNotEquals(known, manual)
        assertEquals(known, lyriconReloadKey("melox:spotify:id", "Title", "Artist", 180_001L, true))
        assertNull(lyriconReloadKey(null, "", "", 0L, true))
    }

    @Test
    fun nonNumericProviderIdsClearLegacyNeteaseLyrics() {
        assertFalse(shouldClearLegacyNeteaseLyrics("123"))
        assertFalse(shouldClearLegacySystemLyrics("123"))
        listOf(null, "", "melox:qq_music:abc", "not-a-number").forEach { mediaId ->
            assertTrue(shouldClearLegacyNeteaseLyrics(mediaId))
            assertTrue(shouldClearLegacySystemLyrics(mediaId))
        }
    }
}
