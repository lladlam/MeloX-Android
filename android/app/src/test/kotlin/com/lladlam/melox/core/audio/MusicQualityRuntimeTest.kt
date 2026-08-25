package com.lladlam.melox.core.audio

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicQualityRuntimeTest {
    @After
    fun resetRuntime() {
        MusicQualityRuntime.selected = MusicQuality.Standard
        MusicQualityRuntime.clear()
    }

    @Test
    fun standardBackgroundAnalysisDoesNotReplaceActiveHiResQuality() {
        val songId = 123L
        MusicQualityRuntime.selected = MusicQuality.HiResolution
        MusicQualityRuntime.recordActual(
            songId = songId,
            requested = MusicQuality.HiResolution,
            actual = MusicQuality.HiResolution,
        )

        MusicQualityRuntime.recordActual(
            songId = songId,
            requested = MusicQuality.Standard,
            actual = MusicQuality.Standard,
        )

        assertEquals(MusicQuality.HiResolution, MusicQualityRuntime.actualFor(songId))
    }

    @Test
    fun reportsForegroundFallbackForCurrentSelection() {
        val songId = 456L
        MusicQualityRuntime.selected = MusicQuality.HiResolution
        MusicQualityRuntime.recordActual(
            songId = songId,
            requested = MusicQuality.HiResolution,
            actual = MusicQuality.Lossless,
        )

        assertEquals(MusicQuality.Lossless, MusicQualityRuntime.actualFor(songId))
    }

    @Test
    fun hidesActualQualityRecordedForAnOldSelection() {
        val songId = 789L
        MusicQualityRuntime.selected = MusicQuality.HiResolution
        MusicQualityRuntime.recordActual(
            songId = songId,
            requested = MusicQuality.HiResolution,
            actual = MusicQuality.HiResolution,
        )

        MusicQualityRuntime.selected = MusicQuality.Standard

        assertNull(MusicQualityRuntime.actualFor(songId))
    }
}
