package com.lladlam.melox.playback

import androidx.media3.common.Player
import com.lladlam.melox.core.model.SearchSong
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXAutoplayPolicyTest {
    @Test
    fun endedPlayerResumesOnlyAfterRecommendationsCreateNextItem() {
        assertTrue(shouldResumeEndedAutoplay(Player.STATE_ENDED, hasNextMediaItem = true))
        assertFalse(shouldResumeEndedAutoplay(Player.STATE_ENDED, hasNextMediaItem = false))
        assertFalse(shouldResumeEndedAutoplay(Player.STATE_READY, hasNextMediaItem = true))
    }

    @Test
    fun providerTrackCanUseStrictNeteaseMatchAsRecommendationSeed() {
        val candidates = listOf(
            song(1, "Target", "Wrong Artist", 180_000),
            song(2, "Target Remix", "Artist", 180_000),
            song(3, "Target", "Artist / Guest", 181_500),
        )

        assertEquals(3L, selectNeteaseAutoplaySeed("Target", "Artist", 180_000, candidates))
        assertNull(selectNeteaseAutoplaySeed("Target", "Artist", 190_000, candidates))
    }

    @Test
    fun recommendationCompletionUsesCurrentEndedStateInsteadOfCapturedFlag() {
        val source = File("src/main/kotlin/com/lladlam/melox/playback/MeloXPlaybackService.kt").readText()

        assertFalse(source.contains("forceAdvance"))
        assertTrue(source.contains("resumeEndedAutoplayIfReady(active)"))
        assertTrue(source.contains("recommendationRetryAfterRealtimeMs"))
    }

    @Test
    fun autoplayPrefetchStartsAtQueueTailAndUsesLongFallbackWindow() {
        val source = File("src/main/kotlin/com/lladlam/melox/playback/MeloXPlaybackService.kt").readText()

        assertTrue(source.contains("active.currentMediaItemIndex >= active.mediaItemCount - 1"))
        assertTrue(source.contains("const val AUTOPLAY_PRELOAD_MS = 60_000L"))
        assertTrue(source.contains(".awaitAll()"))
        assertTrue(source.contains("recommendationsCommitted"))
    }

    private fun song(id: Long, title: String, artist: String, durationMs: Long) = SearchSong(
        id = id,
        name = title,
        artists = artist,
        album = "",
        artworkUrl = null,
        durationMs = durationMs,
    )
}
