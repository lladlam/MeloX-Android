package com.lladlam.melox.core.plugin

import com.lladlam.melox.core.music.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class PluginAudioContractTest {
    private val source = SourceId.parse("plugin:org.example.audio")!!
    private val manifest = PluginManifest(source, "Audio", "1.0.0", 1, "index.js",
        setOf(PluginCapability.Playback, PluginCapability.Download, PluginCapability.Lyrics), setOf("audio.example.com"))
    private val track = MusicTrack(MusicResourceId(source, "opaque"), "Test", listOf(MusicArtistRef(name = "Author")))
    private fun provider(value: String) = PluginMusicProvider(manifest, PluginInvoker { _, _ -> value })

    @Test fun playbackAndDownloadPreserveActualQualityAndRightsStates() = runBlocking {
        val plugin = provider("""{"type":"playable","url":"https://audio.example.com/track.wav","headers":{"X-Audio":"test"},"actualQuality":"standard","format":"wav"}""")
        val playback = plugin.resolvePlayback(track, AudioQualityTier.Lossless) as PlaybackResolution.Playable
        val download = plugin.resolveDownload(track, AudioQualityTier.High) as PlaybackResolution.Playable
        assertEquals(AudioQualityTier.Standard, playback.actualQuality)
        assertEquals(AudioQualityTier.Lossless, playback.requestedQuality)
        assertEquals(AudioQualityTier.High, download.requestedQuality)
        assertEquals("test", download.requestHeaders["X-Audio"])
        assertEquals(PlaybackResolution.LoginRequired, provider("""{"type":"loginRequired"}""").resolveDownload(track, AudioQualityTier.Standard))
        assertEquals(PlaybackResolution.SubscriptionRequired, provider("""{"type":"subscriptionRequired"}""").resolvePlayback(track, AudioQualityTier.Standard))
    }

    @Test fun lyricsRejectCoercionOversizedTranslationsAndUnsortedTimes() = runBlocking {
        val valid = provider("""{"lines":[{"timeMs":0,"text":"Line","translation":"Translation","romanization":"Romanization"}]}""").lyrics(track)
        assertEquals("Line", valid.lines.single().text)
        for (value in listOf("""{"lines":[{"timeMs":"0","text":"Line"}]}""",
            """{"lines":[{"timeMs":1.5,"text":"Line"}]}""",
            """{"lines":[{"timeMs":0,"text":"Line","translation":"${"x".repeat(4097)}"}]}""",
            """{"lines":[{"timeMs":2,"text":"A"},{"timeMs":1,"text":"B"}]}""")) {
            try { provider(value).lyrics(track); fail("invalid lyrics accepted") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun resolutionsRejectLocalUrlsUndeclaredDomainsExpiredUrlsAndConnectionHeaders() = runBlocking {
        for (value in listOf("""{"type":"playable","url":"file:///data/private"}""",
            """{"type":"playable","url":"https://other.example.com/a"}""",
            """{"type":"playable","url":"https://audio.example.com/a","expiresAtEpochMs":1}""",
            """{"type":"playable","url":"https://audio.example.com/a","headers":{"Host":"private"}}""")) {
            try { provider(value).resolveDownload(track, AudioQualityTier.Standard); fail("invalid audio accepted") } catch (_: IllegalArgumentException) { }
        }
    }
}
