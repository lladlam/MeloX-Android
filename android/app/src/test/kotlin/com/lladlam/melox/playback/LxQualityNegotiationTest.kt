package com.lladlam.melox.playback

import com.lladlam.melox.core.music.model.AudioQualityTier
import com.lladlam.melox.core.music.model.MusicArtistRef
import com.lladlam.melox.core.music.model.MusicResourceId
import com.lladlam.melox.core.music.model.MusicSource
import com.lladlam.melox.core.music.model.MusicTrack
import com.lladlam.melox.core.provider.lxuser.negotiateLxQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LxQualityNegotiationTest {
    @Test
    fun userTiersKeepTheirSourceSpecificLxNames() {
        assertEquals("128k", AudioQualityTier.Standard.toLxQuality())
        assertEquals("320k", AudioQualityTier.High.toLxQuality())
        assertEquals("flac", AudioQualityTier.Lossless.toLxQuality())
        assertEquals("hires", AudioQualityTier.HiResolution.toLxQuality())
        assertEquals("atmos_plus", AudioQualityTier.Immersive.toLxQuality())
        assertEquals("master", AudioQualityTier.Master.toLxQuality())
    }

    @Test
    fun forbiddenOldCollapseIntoFlac24BitIsGone() {
        val collapsed = listOf(
            AudioQualityTier.HiResolution,
            AudioQualityTier.Immersive,
            AudioQualityTier.Master,
        ).map { it.toLxQuality() }
        assertEquals(listOf("hires", "atmos_plus", "master"), collapsed)
    }

    @Test
    fun negotiationPicksTheBestDeclaredQualityAtOrBelowTheRequest() {
        assertEquals("master", negotiateLxQuality(listOf("128k", "flac", "master"), "master"))
        assertEquals("hires", negotiateLxQuality(listOf("128k", "flac", "hires"), "master"))
        assertEquals("atmos", negotiateLxQuality(listOf("flac", "atmos"), "atmos_plus"))
        assertEquals("flac", negotiateLxQuality(listOf("128k", "flac"), "master"))
        assertEquals("320k", negotiateLxQuality(listOf("128k", "320k"), "hires"))
    }

    @Test
    fun fallbackOrderTriesOnlyQualitiesAtOrBelowTheRequest() {
        assertEquals(
            listOf("master", "atmos_plus", "atmos", "hires", "flac24bit", "flac", "320k", "128k"),
            lxQualityFallbacks("master"),
        )
        assertEquals(listOf("hires", "flac24bit", "flac", "320k", "128k"), lxQualityFallbacks("hires"))
        assertEquals(listOf("atmos", "hires", "flac24bit", "flac", "320k", "128k"), lxQualityFallbacks("atmos"))
    }

    @Test
    fun sourceQualityMapsBackToTheClosestTier() {
        assertEquals(AudioQualityTier.Standard, lxQualityTier("128k"))
        assertEquals(AudioQualityTier.High, lxQualityTier("320k"))
        assertEquals(AudioQualityTier.Lossless, lxQualityTier("flac"))
        assertEquals(AudioQualityTier.HiResolution, lxQualityTier("flac24bit"))
        assertEquals(AudioQualityTier.HiResolution, lxQualityTier("hires"))
        assertEquals(AudioQualityTier.Immersive, lxQualityTier("atmos"))
        assertEquals(AudioQualityTier.Immersive, lxQualityTier("atmos_plus"))
        assertEquals(AudioQualityTier.Master, lxQualityTier("master"))
        assertNull(lxQualityTier(null))
        assertNull(lxQualityTier("unknown"))
    }

    @Test
    fun lxPlaybackMatchUsesTitleArtistAndVersionBeforeDuration() {
        val target = track("提瓦特民谣", "宴宁", 240_000L)
        assertTrue(isSafeLxPlaybackMatch("提瓦特民谣", "宴宁", 240_000L, target))
        assertTrue(
            isSafeLxPlaybackMatch(
                "提瓦特民谣 feat. 陶典", "宴宁 / 陶典", 240_000L,
                track("提瓦特民谣", "宴宁 / 陶典", 241_500L),
            ),
        )
        assertFalse(
            isSafeLxPlaybackMatch(
                "提瓦特民谣", "宴宁", 240_000L,
                track("提瓦特民谣 (Live)", "宴宁", 240_000L),
            ),
        )
        assertFalse(
            isSafeLxPlaybackMatch(
                "提瓦特民谣", "宴宁", 240_000L,
                track("提瓦特民谣", "其他歌手", 240_000L),
            ),
        )
    }

    @Test
    fun lxPlaybackMatchAcceptsMinorDurationMetadataDrift() {
        assertTrue(
            isSafeLxPlaybackMatch(
                "提瓦特民谣", "宴宁", 240_000L,
                track("提瓦特民谣", "宴宁", 241_500L),
            ),
        )
        assertFalse(
            isSafeLxPlaybackMatch(
                "提瓦特民谣", "宴宁", 240_000L,
                track("提瓦特民谣", "宴宁", 243_000L),
            ),
        )
    }

    private fun track(title: String, artist: String, durationMs: Long) = MusicTrack(
        id = MusicResourceId(MusicSource.Kugou, title),
        title = title,
        artists = listOf(MusicArtistRef(name = artist)),
        durationMs = durationMs,
    )
}
