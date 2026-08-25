package com.lladlam.melox.playback

import com.lladlam.melox.core.audio.MusicQuality
import com.lladlam.melox.core.audio.MusicQualityRuntime
import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderPlaybackQualityRuntimeTest {
    @After
    fun resetRuntime() {
        MusicQualityRuntime.selected = MusicQuality.Standard
        ProviderPlaybackQualityRuntime.clear()
    }

    @Test
    fun standardBackgroundAnalysisDoesNotReplaceProviderHiResQuality() {
        val id = MusicResourceId(MusicSource.Kugou, "track-hash")
        MusicQualityRuntime.selected = MusicQuality.HiResolution
        ProviderPlaybackQualityRuntime.recordActual(
            id = id,
            requested = AudioQualityTier.HiResolution,
            actual = AudioQualityTier.HiResolution,
        )

        ProviderPlaybackQualityRuntime.recordActual(
            id = id,
            requested = AudioQualityTier.Standard,
            actual = AudioQualityTier.Standard,
        )

        assertEquals(AudioQualityTier.HiResolution, ProviderPlaybackQualityRuntime.actualFor(id))
    }
}
