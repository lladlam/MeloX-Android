package com.lladlam.melox.ui.player

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeloXPlayerInitializationTest {
    @Test
    fun reenteringCurrentTrackStartsAtReportedPosition() {
        val result = lyricsPanelPlaybackInitialization(
            isFirstComposition = true,
            mediaIdChanged = false,
            reportedPositionMs = 73_000L,
        )

        assertEquals(73_000L, result.positionMs)
        assertFalse(result.holdAtTrackStart)
        assertFalse(result.resetListToStart)
    }

    @Test
    fun realTrackChangeResetsAndHoldsAtStart() {
        val result = lyricsPanelPlaybackInitialization(
            isFirstComposition = false,
            mediaIdChanged = true,
            reportedPositionMs = 0L,
        )

        assertEquals(0L, result.positionMs)
        assertTrue(result.holdAtTrackStart)
        assertTrue(result.resetListToStart)
    }

    @Test
    fun realTrackChangeRejectsPositionFromPreviousTrack() {
        val result = lyricsPanelPlaybackInitialization(
            isFirstComposition = false,
            mediaIdChanged = true,
            reportedPositionMs = 184_000L,
        )

        assertEquals(0L, result.positionMs)
        assertTrue(result.holdAtTrackStart)
        assertTrue(result.resetListToStart)
    }

    @Test
    fun pausedMiddlePositionAndAdvanceInitializeHighlightClock() {
        val result = lyricsPanelPlaybackInitialization(
            isFirstComposition = true,
            mediaIdChanged = false,
            reportedPositionMs = 91_500L,
        )

        assertEquals(92_250L, initialLyricsHighlightPositionMs(result.positionMs, 750L))
    }

    @Test
    fun fullArtworkRequestUsesMaximumFramePixels() {
        assertEquals(900, fullArtworkRequestSizePx(fullArtworkSizeDp = 360f, density = 2.5f))
        assertEquals(1, fullArtworkRequestSizePx(fullArtworkSizeDp = 0f, density = 3f))
    }

    @Test
    fun sharedArtworkOwnsSizedRequestWhileArtworkDefaultsToUrl() {
        val sourceRoot = File("src/main/kotlin/com/lladlam/melox/ui/player")
        val sharedHost = File(sourceRoot, "MeloXIOSNowPlayingSharedHost.kt").readText()
        val playerUi = File(sourceRoot, "MeloXPlayerUi.kt").readText()

        assertTrue(sharedHost.contains("remember(context, state.artworkUrl, fullArtworkSizePx)"))
        assertTrue(sharedHost.contains(".size(fullArtworkSizePx, fullArtworkSizePx)"))
        assertTrue(sharedHost.contains(".precision(Precision.INEXACT)"))
        assertTrue(sharedHost.contains("model = fullArtworkModel"))
        assertTrue(playerUi.contains("model: Any? = url"))
        assertTrue(playerUi.contains("model = model"))
    }
}
